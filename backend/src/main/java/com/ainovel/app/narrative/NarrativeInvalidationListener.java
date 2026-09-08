package com.ainovel.app.narrative;
import com.ainovel.app.manuscript.attribution.SceneContentEditedEvent;
import com.ainovel.app.manuscript.attribution.ManuscriptRollbackEvent;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.story.repo.OutlineRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NarrativeInvalidationListener {
    private final NarrativeService narrative;
    private final ManuscriptRepository manuscripts;
    private final OutlineRepository outlines;
    public NarrativeInvalidationListener(NarrativeService narrative, ManuscriptRepository manuscripts, OutlineRepository outlines) {
        this.narrative = narrative; this.manuscripts = manuscripts; this.outlines = outlines;
    }
    @EventListener public void edited(SceneContentEditedEvent event) { narrative.reconcileManuscript(event.manuscriptId()); }
    @EventListener public void rollback(ManuscriptRollbackEvent event) { narrative.reconcileManuscript(event.manuscriptId()); }
    @EventListener public void changed(NarrativeSourceChanged event) {
        if (event.manuscriptId() != null) narrative.reconcileManuscript(event.manuscriptId());
        if (event.outlineId() != null) outlines.findById(event.outlineId()).ifPresent(outline ->
                manuscripts.findByOutline(outline).forEach(m -> narrative.reconcileManuscript(m.getId())));
    }
}
