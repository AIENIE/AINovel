package com.ainovel.app.modelconfig;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.common.CurrentUserResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Supplies confirmed business selections before controller writes or asynchronous dispatch. */
@Order(Ordered.LOWEST_PRECEDENCE-20)
public class AiQuoteRequestFilter extends OncePerRequestFilter {
    private final AiActionPlanner planner;
    private final AiQuoteService quotes;
    private final CurrentUserResolver users;
    private final ObjectMapper json;
    public AiQuoteRequestFilter(AiActionPlanner planner,AiQuoteService quotes,CurrentUserResolver users,ObjectMapper json) {
        this.planner=planner;this.quotes=quotes;this.users=users;this.json=json;
    }
    private String target(HttpServletRequest request) {
        String path=request.getRequestURI();String context=request.getContextPath();
        if(!context.isBlank()&&path.startsWith(context))path=path.substring(context.length());
        if(path.startsWith("/api/"))path=path.substring(4);
        return path+(request.getQueryString()==null?"":"?"+request.getQueryString());
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !planner.candidate(request.getMethod(),target(request));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated()||"anonymousUser".equals(auth.getPrincipal())) {chain.doFilter(request,response);return;}
        byte[] body=request.getInputStream().readAllBytes();
        var principal=auth.getPrincipal() instanceof UserDetails details?details:
                org.springframework.security.core.userdetails.User.withUsername(auth.getName()).password("").authorities("ROLE_USER").build();
        AiSelectionContext.Snapshot snapshot;
        try {
            var input=body.length==0?json.createObjectNode():json.readTree(body);
            if(planner.features(target(request),input).isEmpty()){chain.doFilter(new BodyRequest(request,body),response);return;}
            snapshot=quotes.accept(users.require(principal),target(request),input,request.getHeader("X-AI-Quote-Id"),request.getHeader("Idempotency-Key"));
        } catch(ApiStatusException ex) {
            response.setStatus(ex.getStatus().value());response.setContentType("application/json");
            json.writeValue(response.getOutputStream(),java.util.Map.of("code",ex.getMessage(),"message",ex.getMessage()));return;
        }
        try(var ignored=AiSelectionContext.install(snapshot)) {chain.doFilter(new BodyRequest(request,body),response);}
        catch(IOException|ServletException e){throw e;}catch(Exception e){throw new ServletException(e);}
    }
    private static final class BodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private BodyRequest(HttpServletRequest request,byte[] body){super(request);this.body=body;}
        @Override public ServletInputStream getInputStream() {
            var input=new ByteArrayInputStream(body);
            return new ServletInputStream() {
                public int read(){return input.read();}
                public boolean isFinished(){return input.available()==0;}
                public boolean isReady(){return true;}
                public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}
            };
        }
        @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
    }
}
