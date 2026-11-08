package com.pikminx.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class OverlayHostTest {
    @Test
    public void collapsingThePanelPreservesTheWorkflowProjection() {
        OverlayHost.PresentationState state = OverlayHost.PresentationState.initial();
        OverlayHost.WorkflowProjection workflow = new OverlayHost.WorkflowProjection(
                OverlayHost.Workflow.DISPATCH, true, true, false);

        state.expandPanel();
        state.renderWorkflow(workflow);
        state.collapsePanel();

        assertFalse(state.panelExpanded());
        assertTrue(state.iconVisible());
        assertSame(workflow, state.workflow());
        assertTrue(state.workflow().running());
    }

    @Test
    public void selectingAFeatureTabDoesNotChangeWorkflowState() {
        OverlayHost.PresentationState state = OverlayHost.PresentationState.initial();
        OverlayHost.WorkflowProjection workflow = new OverlayHost.WorkflowProjection(
                OverlayHost.Workflow.PLANTING, true, true, false);

        state.renderWorkflow(workflow);
        state.selectFeatureTab(OverlayHost.FeatureTab.DISPATCH);

        assertSame(OverlayHost.FeatureTab.DISPATCH, state.featureTab());
        assertSame(workflow, state.workflow());
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
}
