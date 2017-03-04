import com.westplay.reactivercon.RconConnection;
import com.westplay.reactivercon.RconPacket;
import io.reactivex.observers.DefaultObserver;

/**
 * Created by westplay on 04/03/17.
 */
public class Main {

    public static void main(String[] args) {

        RconConnection rconConnection = new RconConnection();

        rconConnection.sendCommand("").subscribe(new DefaultObserver<Integer>() {
            @Override
            public void onNext(Integer value) {
                System.out.println(value.toString());
            }

            @Override
            public void onError(Throwable e) {
                System.out.println("On error:\t" + e.getMessage());
            }

            @Override
            public void onComplete() {

            }
        });

    }

}
