package com.westplay.reactivercon.exception;

/**
 * Created by andreas on 27/02/17.
 */

public class RconAuthenticationException extends Exception {

    public RconAuthenticationException() {
        super("Authentication failed");
    }

}
