package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.pikminx.helper.platform.diagnostics.AdmissionDiagnostics;
import com.pikminx.helper.platform.diagnostics.WorkflowDiagnostics;

import org.junit.Test;

public final class DiagnosticsPassivityTest {
    @Test
    public void recordingAnAllowedDecisionDoesNotDispatchAnActionOrChangeWorkflowState() {
        AdmissionDiagnostics admissionDiagnostics = new AdmissionDiagnostics();
        WorkflowDiagnostics workflowDiagnostics = new WorkflowDiagnostics();
        ActionAdmission.Decision decision = new ActionAdmission.Decision(
                true, ActionAdmission.RejectionReason.NONE);
        int[] actionCount = {0};
        int workflowState = 17;

        admissionDiagnostics.recordActionCheck("TEST", null, 1_000L, decision);
        workflowDiagnostics.recordAdmission(decision);

        assertEquals(0, actionCount[0]);
        assertEquals(17, workflowState);
        assertEquals(1L, admissionDiagnostics.snapshot().admittedCount());
        assertEquals(1L, workflowDiagnostics.snapshot().count(
                WorkflowDiagnostics.Counter.ACTIONS_ADMITTED));

        assertTrue(ActionAdmission.performIfAllowed(decision, () -> {
            actionCount[0]++;
            return true;
        }));
        assertEquals(1, actionCount[0]);
        assertEquals(17, workflowState);
    }
}
