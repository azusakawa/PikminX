package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class OverlayHostTest {
    @Test
    public void collapsingThePanelDoesNotStopTheWorkflowProjection() {
        RecordingCommands commands = new RecordingCommands();
        OverlayHost.PresentationState state = OverlayHost.PresentationState.initial();
        OverlayHost.WorkflowProjection workflow = new OverlayHost.WorkflowProjection(
                OverlayHost.Workflow.MUSHROOM_SCAN, true, true, false);

        state.expandPanel();
        state.renderWorkflow(workflow);
        state.collapsePanel();

        assertFalse(state.panelExpanded());
        assertTrue(state.iconVisible());
        assertTrue(state.workflow().running());
        assertEquals(0, commands.stopMushroomScanCalls);
    }

    @Test
    public void expandingAndSelectingTabsDoNotStartAWorkflow() {
        RecordingCommands commands = new RecordingCommands();
        OverlayHost.PresentationState state = OverlayHost.PresentationState.initial();

        state.expandPanel();
        state.selectFeatureTab(OverlayHost.FeatureTab.MUSHROOM);
        state.selectMushroomTab(MushroomUiState.Tab.SCAN);

        assertEquals(0, commands.startMushroomScanCalls);
        assertEquals(0, commands.startMushroomPatrolCalls);
        assertEquals(0, commands.stopMushroomScanCalls);
        assertSame(OverlayHost.FeatureTab.MUSHROOM, state.featureTab());
        assertSame(MushroomUiState.Tab.SCAN, state.mushroomTab());
    }

    @Test
    public void openingAndClosingMapDoNotChangePatrolCommands() {
        RecordingCommands commands = new RecordingCommands();
        OverlayHost.PresentationState state = OverlayHost.PresentationState.initial();

        state.selectFeatureTab(OverlayHost.FeatureTab.MUSHROOM);
        state.selectMushroomTab(MushroomUiState.Tab.MAP);
        state.selectMushroomTab(MushroomUiState.Tab.SCAN);

        assertEquals(0, commands.startMushroomPatrolCalls);
        assertEquals(0, commands.stopMushroomPatrolCalls);
    }

    @Test
    public void explicitMushroomCommandsDelegateExactlyOnce() {
        RecordingCommands commands = new RecordingCommands();
        OverlayHost.CommandRouter router = new OverlayHost.CommandRouter(commands);

        router.startMushroomScan();
        router.rescanMushroomScan();
        router.stopMushroomScan();
        router.pauseMushroomPatrol();
        router.resumeMushroomPatrol();

        assertEquals(1, commands.startMushroomScanCalls);
        assertEquals(1, commands.rescanMushroomScanCalls);
        assertEquals(1, commands.stopMushroomScanCalls);
        assertEquals(1, commands.pauseMushroomPatrolCalls);
        assertEquals(1, commands.resumeMushroomPatrolCalls);
    }

    @Test
    public void onlyMushroomPatrolProjectsResumeControls() {
        OverlayHost.WorkflowProjection planting = new OverlayHost.WorkflowProjection(
                OverlayHost.Workflow.PLANTING, true, true, false);
        OverlayHost.WorkflowProjection patrolRunning = new OverlayHost.WorkflowProjection(
                OverlayHost.Workflow.MUSHROOM_PATROL, true, true, false);
        OverlayHost.WorkflowProjection patrolPaused = new OverlayHost.WorkflowProjection(
                OverlayHost.Workflow.MUSHROOM_PATROL, true, true, true);

        assertFalse(planting.showsPause());
        assertFalse(planting.showsResume());
        assertTrue(patrolRunning.showsPause());
        assertFalse(patrolRunning.showsResume());
        assertFalse(patrolPaused.showsPause());
        assertTrue(patrolPaused.showsResume());
    }

    @Test
    public void workflowRehydrationRendersWithoutDispatchingStart() {
        RecordingCommands commands = new RecordingCommands();
        OverlayHost.PresentationState state = OverlayHost.PresentationState.initial();
        OverlayHost.WorkflowProjection running = new OverlayHost.WorkflowProjection(
                OverlayHost.Workflow.MUSHROOM_SCAN, true, true, false);

        state.renderWorkflow(running);
        state.expandPanel();

        assertSame(running, state.workflow());
        assertEquals(0, commands.startMushroomScanCalls);
    }

    @Test
    public void staleCaptureRestoreCannotOverwriteNewerPresentation() {
        OverlayHost.PresentationState state = OverlayHost.PresentationState.initial();
        state.expandPanel();

        OverlayHost.CaptureToken older = state.prepareCaptureMask();
        OverlayHost.CaptureToken newer = state.prepareCaptureMask();

        assertFalse(state.restoreCaptureMask(older));
        assertTrue(state.captureMasked());
        assertTrue(state.restoreCaptureMask(newer));
        assertTrue(state.panelExpanded());
        assertFalse(state.captureMasked());
    }

    @Test
    public void captureRestoresCollapsedPresentation() {
        OverlayHost.PresentationState state = OverlayHost.PresentationState.initial();

        OverlayHost.CaptureToken token = state.prepareCaptureMask();

        assertTrue(state.restoreCaptureMask(token));
        assertTrue(state.iconVisible());
        assertFalse(state.panelExpanded());
    }

    @Test
    public void destroyClearsPresentationAttachmentState() {
        OverlayHost.PresentationState state = OverlayHost.PresentationState.initial();
        state.expandPanel();
        state.prepareCaptureMask();

        state.destroy();

        assertTrue(state.destroyed());
        assertFalse(state.panelExpanded());
        assertFalse(state.iconVisible());
        assertFalse(state.captureMasked());
    }

    private static final class RecordingCommands implements OverlayHost.Commands {
        int startMushroomScanCalls;
        int rescanMushroomScanCalls;
        int stopMushroomScanCalls;
        int startMushroomPatrolCalls;
        int pauseMushroomPatrolCalls;
        int resumeMushroomPatrolCalls;
        int stopMushroomPatrolCalls;

        @Override
        public void startMushroomScan() {
            startMushroomScanCalls++;
        }

        @Override
        public void rescanMushroomScan() {
            rescanMushroomScanCalls++;
        }

        @Override
        public void stopMushroomScan() {
            stopMushroomScanCalls++;
        }

        @Override
        public void startMushroomPatrol() {
            startMushroomPatrolCalls++;
        }

        @Override
        public void pauseMushroomPatrol() {
            pauseMushroomPatrolCalls++;
        }

        @Override
        public void resumeMushroomPatrol() {
            resumeMushroomPatrolCalls++;
        }

        @Override
        public void stopMushroomPatrol() {
            stopMushroomPatrolCalls++;
        }
    }
}
