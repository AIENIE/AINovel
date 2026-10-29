package com.ainovel.app.quality;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.AiUsageContext;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.quality.language.LanguageProjection;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AiSlopJudgeClient implements SlopJudgeClient {
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private SlopInputAdmission admission=new SlopInputAdmission(20000,100000,24000);
    public AiSlopJudgeClient(AiService aiService,ObjectMapper objectMapper) { this.aiService=aiService; this.objectMapper=objectMapper; }
    @Override public SlopJudgeResult judge(User user,SlopQualityRequest request,SlopHeuristicResult local) { return judge(user,request,local,null); }
    @Override public SlopJudgeResult judge(User user,SlopQualityRequest request,SlopHeuristicResult local,AiUsageContext usage) {
        AiChatRequest chat=SlopEvidence.request(request,local,"slop.judge",objectMapper);
        admission.requireFits(chat);
        String raw=(usage==null?aiService.chat(user,chat):aiService.chat(user,chat,usage)).content();
        var review=new SlopEvidence.ReviewData();
        // Drafts are plain text, not editor HTML. Escape tags before projecting so literal protocol markers remain visible.
        review.projection=LanguageProjection.of(org.springframework.web.util.HtmlUtils.htmlEscape(request.candidateText()));
        review.request=chat; review.localSignals=local.issues(); review.localRisk=local.overallRiskScore(); review.raw=raw;
        var parsed=SlopEvidence.parse(raw,review.projection,objectMapper);
        review.findings=parsed.findings(); review.coverage=parsed.complete()?"COMPLETE":"PARTIAL"; review.reason=parsed.summary();
        var issues=parsed.findings().stream().filter(f->f.confirmed() && f.kind()!=SlopEvidence.Kind.STYLE).map(SlopEvidence::draft).toList();
        return new SlopJudgeResult(parsed.risk(),false,issues,parsed.summary(),
                SlopQualitySignals.fromIssues(parsed.risk(),SlopEvidence.severity(parsed.risk()),issues),review);
    }
}
