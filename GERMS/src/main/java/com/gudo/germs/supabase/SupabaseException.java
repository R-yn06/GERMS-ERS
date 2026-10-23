package com.gudo.germs.supabase;

public class SupabaseException extends Exception {
    private final int status;

    public SupabaseException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** HTTP status, or 0 when the server could not be reached. */
    public int status() { return status; }
    public boolean isNetworkError() { return status == 0; }
}
