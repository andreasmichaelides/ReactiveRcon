package com.westplay.reactivercon.exception;

/**
 * Created by westplay on 04/03/17.
 */
public class NotConnectedException extends Exception {

    public NotConnectedException() {
        super("Not connected to an Rcon server");
    }
}
