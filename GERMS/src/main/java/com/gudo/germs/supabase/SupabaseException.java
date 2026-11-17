package com.gudo.germs.supabase;

/** Represents a failed Supabase request and its HTTP status. */
public class SupabaseException extends Exception {
    private final int status;

    /** Creates a supabase exception instance. */
    public SupabaseException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** HTTP status, or 0 when the server could not be reached. */
    public int status() { return status; }
    /** Reports whether the failure occurred because the server could not be reached. */
    public boolean isNetworkError() { return status == 0; }
}
