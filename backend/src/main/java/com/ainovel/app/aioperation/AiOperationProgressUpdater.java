package com.ainovel.app.aioperation;

public interface AiOperationProgressUpdater {
    void step(String label, int completedSteps, int totalSteps);
}
