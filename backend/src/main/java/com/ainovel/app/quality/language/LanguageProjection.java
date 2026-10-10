package com.ainovel.app.quality.language;

import org.springframework.web.util.HtmlUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** QUALITY_PARAGRAPH_V2: UTF-16 offsets, without changing any historical projection policy. */
public final class LanguageProjection {
    public static final String VERSION = "QUALITY_PARAGRAPH_V2";
    private static final Pattern TAG = Pattern.compile("(?is)<!--.*?-->|<[^>]*>");
    private static final Set<String> BLOCKS = Set.of("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "li", "tr");
    private static final Set<String> SAFE = Set.of("p", "div", "br", "span", "strong", "b", "em", "i", "u", "s", "a", "h1", "h2", "h3", "h4", "h5", "h6");
    public record Paragraph(int index, int start, int end, String text) {}
    public record Projection(String version, String html, String text, String htmlHash, String textHash,
                             List<Integer> rawStart, List<Integer> rawEnd, List<Paragraph> paragraphs, boolean simple) {
        public boolean validRange(int start, int end) {
            return start >= 0 && end > start && end <= text.length() && boundary(text,start) && boundary(text,end);
        }
        public boolean canReplace(int start, int end) {
            if (!simple || !validRange(start,end) || paragraphAt(start,end)==null) return false;
            int a=rawStart.get(start), b=rawEnd.get(end-1);
            return a>=0 && b>a && !html.substring(a,b).contains("<")
                    && (start==0 || !rawStart.get(start).equals(rawStart.get(start-1)))
                    && (end==text.length() || !rawEnd.get(end-1).equals(rawEnd.get(end)));
        }
        public String replace(int start, int end, String expected, String replacement) {
            if (!canReplace(start,end) || !text.substring(start,end).equals(expected) || replacement==null
                    || replacement.contains("\n") || replacement.contains("\r")) throw new IllegalArgumentException("LANGUAGE_RANGE_NOT_APPLICABLE");
            int a=rawStart.get(start), b=rawEnd.get(end-1);
            // Escape even in legacy plain-text sections: text containing '<' is never executable markup.
            return html.substring(0,a)+HtmlUtils.htmlEscape(replacement)+html.substring(b);
        }
        public Paragraph paragraphAt(int start, int end) {
            return paragraphs.stream().filter(p->start>=p.start && end<=p.end).findFirst().orElse(null);
        }
    }
    public static Projection of(String source) {
        String html=Objects.requireNonNullElse(source,"");
        StringBuilder text=new StringBuilder(); var starts=new ArrayList<Integer>(); var ends=new ArrayList<Integer>();
        var tags=TAG.matcher(html); int offset=0; boolean simple=true; String hidden=null;
        var openedBlocks=new HashMap<String,Deque<Integer>>();
        while(tags.find()) {
            if(hidden==null) append(html,offset,tags.start(),text,starts,ends);
            String token=tags.group(); String name=token.replaceFirst("^</?\\s*","").split("[\\s/>]",2)[0].toLowerCase(Locale.ROOT);
            boolean closing=token.startsWith("</");
            if(Set.of("script","style","template").contains(name)) { simple=false; hidden=closing?null:name; }
            if(!SAFE.contains(name)) simple=false;
            if(hidden==null && name.equals("br")) {
                // Explicit consecutive line breaks are source content, not redundant block separators.
                text.append('\n'); starts.add(-2); ends.add(-2);
            } else if(hidden==null && BLOCKS.contains(name)) {
                var opened=openedBlocks.computeIfAbsent(name,k->new ArrayDeque<>());
                boolean emptyBlock=closing && !opened.isEmpty() && opened.pop()==text.length();
                if((!text.isEmpty() && text.charAt(text.length()-1)!='\n') || emptyBlock) {
                    text.append('\n'); starts.add(-1); ends.add(-1);
                }
                if(!closing) opened.push(text.length());
            }
            offset=tags.end();
        }
        if(hidden==null) append(html,offset,html.length(),text,starts,ends);
        // Keep source whitespace. Only remove the synthetic terminal block separator.
        if(!text.isEmpty() && starts.getLast()==-1) { text.setLength(text.length()-1); starts.removeLast(); ends.removeLast(); }
        var paragraphs=new ArrayList<Paragraph>(); String value=text.toString(); int start=0;
        for(int i=0;i<=value.length();i++) if(i==value.length() || value.charAt(i)=='\n') {
            if(i>start && !value.substring(start,i).isBlank()) paragraphs.add(new Paragraph(paragraphs.size(),start,i,value.substring(start,i)));
            start=i+1;
        }
        return new Projection(VERSION,html,value,hash(html),hash(value),List.copyOf(starts),List.copyOf(ends),List.copyOf(paragraphs),simple);
    }
    private static void append(String raw,int from,int to,StringBuilder text,List<Integer> starts,List<Integer> ends) {
        for(int i=from;i<to;) {
            int next=i+1; String value=raw.substring(i,next);
            if(raw.charAt(i)=='&') {
                int semi=raw.indexOf(';',i+1);
                if(semi>=0 && semi<to && semi-i<=32) {
                    String entity=raw.substring(i,semi+1), decoded=HtmlUtils.htmlUnescape(entity);
                    if(!entity.equals(decoded)) { value=decoded; next=semi+1; }
                }
            } else if(raw.charAt(i)=='\r') { value="\n"; if(next<to && raw.charAt(next)=='\n') next++; }
            text.append(value); for(int k=0;k<value.length();k++) { starts.add(i); ends.add(next); } i=next;
        }
    }
    private static boolean boundary(String text,int offset) {
        return offset==0 || offset==text.length() || !(Character.isHighSurrogate(text.charAt(offset-1)) && Character.isLowSurrogate(text.charAt(offset)));
    }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
