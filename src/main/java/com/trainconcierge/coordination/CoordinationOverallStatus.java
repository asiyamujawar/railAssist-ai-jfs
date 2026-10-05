package com.trainconcierge.coordination;

/**
 * Overall outcome status of an end-to-end travel coordination workflow execution.
 */
public enum CoordinationOverallStatus {
    /** All existing journey reservations (hotel, cab) were successfully updated. */
    SUCCESS,

    /** At least one component succeeded but another failed — requires attention/retry. */
    PARTIAL_FAILURE,

    /** Workflow failed completely or all active reservations failed to update. */
    FAILED,

    /** No hotel or cab reservations were associated with the journey — no action needed. */
    NO_ACTION_REQUIRED
}
