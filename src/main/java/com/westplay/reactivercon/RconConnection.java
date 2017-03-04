package com.westplay.reactivercon;

import com.westplay.reactivercon.exception.NotConnectedException;
import com.westplay.reactivercon.exception.RconAuthenticationException;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.internal.operators.observable.ObservableCreate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Created by westplay on 26/02/17.
 */

public class RconConnection {

    public static final int SOCKET_LISTENER_INTERVAL = 5;
    private boolean isAuthenticated = false;
    private Random random;
    private Socket socket;
    // ObservableEmmiters that wait for a response with a specific ID from the RCON server
    private Map<Integer, ObservableEmitter<RconPacket>> observableEmitterMap;

    public RconConnection() {
        random = new Random();
        observableEmitterMap = new HashMap<>();
    }

    /**
     * Authenticates to the Rcon server and returns an observable that gets invoked by all the server responses (RconPackets)
     * It will throw an RconAuthenticationException if the credentials are wrong
     */
    public Observable<RconPacket> authenticate(final String ipAddress, final int port, final String password) {
        Integer authRequestId = getRequestId();

        return Observable.using(
                getSocketResourceSupplier(ipAddress, port, authRequestId, password),
                getRconPacketSourceSupplier(authRequestId),
                getSocketDisposer()
        );
    }

    /**
     * Creates a new socket instance with the IP address and socket port of the server as well as sending an
     * authentication command
     */
    private Callable<Socket> getSocketResourceSupplier(final String ipAddress, final int port, final int authRequestId, final String password) {
        return new Callable<Socket>() {
            @Override
            public Socket call() throws Exception {
                if (isSocketConnected()) {
                    throw new IllegalStateException("Already connected, disconnect before connecting again");
                } else {
                    socket = new Socket(ipAddress, port);

                    // Create and send the authorization packet
                    RconPacket rconPacket = new RconPacket(authRequestId, RconPacket.SERVERDATA_AUTH, password);
                    sendPacket(rconPacket, socket);

                    return socket;
                }
            }
        };
    }

    /**
     * Handles every server response as well as the authentication state depending on the auth server response
     */
    private Function<Socket, ObservableSource<? extends RconPacket>> getRconPacketSourceSupplier(final int authRequestId) {
        return new Function<Socket, ObservableSource<? extends RconPacket>>() {
            @Override
            public ObservableSource<? extends RconPacket> apply(final Socket socket) throws Exception {
                return Observable.interval(SOCKET_LISTENER_INTERVAL, TimeUnit.MILLISECONDS).map(new Function<Long, RconPacket>() {
                    @Override
                    public RconPacket apply(Long aLong) throws Exception {
                        RconPacket rconPacket = readPacket(socket);
                        int packetRequestId = rconPacket.getRequestId();

                        if (!isAuthenticated) {
                            if (packetRequestId == authRequestId && rconPacket.getType() == RconPacket.SERVERDATA_AUTH_RESPONSE) {
                                isAuthenticated = true;
                            } else if (packetRequestId == -1) {
                                isAuthenticated = false;
                                throw new RconAuthenticationException();
                            }
                        } else {
                            ObservableEmitter<RconPacket> rconPacketObservableEmitter
                                    = observableEmitterMap.get(packetRequestId);
                            if (rconPacketObservableEmitter != null) {
                                observableEmitterMap.remove(packetRequestId);
                                rconPacketObservableEmitter.onNext(rconPacket);
                                rconPacketObservableEmitter.onComplete();
                            }
                        }

                        return rconPacket;
                    }
                });
            }
        };
    }

    private Consumer<Socket> getSocketDisposer() {
        return new Consumer<Socket>() {
            @Override
            public void accept(Socket socket) throws Exception {
                disconnect();
            }
        };
    }

    /**
     * Sends a command to the server which returns an Observable with the id of the RconPacket sent
     * @param command
     * @return
     */
    public Observable<Integer> sendCommand(final String command) {
        return ObservableCreate.fromCallable(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                int commandRequestId = getRequestId();
                sendCommandPacket(commandRequestId, command);
                return commandRequestId;
            }
        });
    }

    /**
     * Sends a command to the server and returns an observable which will be invoked when the server responds for the
     * specific command that has been sent
     */
    public Observable<RconPacket> sendCommandExpectingResponse(final String command) {
        return ObservableCreate.create(new ObservableOnSubscribe<RconPacket>() {
            @Override
            public void subscribe(ObservableEmitter<RconPacket> emitter) throws Exception {
                int commandRequestId = getRequestId();
                sendCommandPacket(commandRequestId, command);
                observableEmitterMap.put(commandRequestId, emitter);
            }
        });
    }


    public Observable<Boolean> disconnect() {
        return ObservableCreate.create(new ObservableOnSubscribe<Boolean>() {
            @Override
            public void subscribe(ObservableEmitter<Boolean> emitter) throws Exception {
                try {
                    closeSocket();
                    emitter.onNext(true);
                } catch (IOException e) {
                    emitter.onError(e);
                } finally {
                    isAuthenticated = false;
                    socket = null;
                    emitter.onComplete();
                }
            }
        });
    }

    private int getRequestId() {
        return random.nextInt();
    }

    private void sendCommandPacket(int commandRequestId, String command) throws IOException, NotConnectedException {
        sendPacket(new RconPacket(
                commandRequestId,
                RconPacket.SERVERDATA_EXECCOMMAND,
                command
        ), socket);
    }
    private void sendPacket(RconPacket rconPacket, Socket socket) throws IOException, NotConnectedException {
        if (!isSocketConnected()) {
            throw new NotConnectedException();
        }
        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(rconPacket.getAsByteArray());
        outputStream.flush();
    }

    private RconPacket readPacket(Socket socket) throws IOException {
        return RconPacket.createFromStream(socket.getInputStream());
    }

    private boolean isSocketConnected() {
        return socket != null && socket.isConnected();
    }

    private void closeSocket() throws IOException {
        if (isSocketConnected()) {
            socket.close();
        }

        // Clear the ObservableEmitter map to avoid memory leaks
        observableEmitterMap.clear();
    }

}
