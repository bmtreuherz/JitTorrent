package tests;

import com.company.services.Client;
import com.company.services.Server;

import static java.lang.System.exit;

// NOTE: These aren't actually tests... Just a class I've been using to "test" code as I write it.
public class Test {

    private static final String HOST_NAME = "localhost";
    private static final int PORT = 8000;

    public static void main(String[] args) {

        verifyServer();

    }

    private static void verifyServer(){
        Server server = new Server();

        (new Thread(new ServerStarter(server, PORT))).start();

        Client client = new Client();
        client.run(HOST_NAME, PORT);
    }

    private static class ServerStarter implements Runnable{

        private Server server;
        private int port;

        ServerStarter(Server server, int port){
            this.server = server;
            this.port = port;
        }

        @Override
        public void run() {
            try{
                server.start(port);
            } catch(Exception e){
                System.out.println(e);
                exit(1);
            }
        }
    }
}
