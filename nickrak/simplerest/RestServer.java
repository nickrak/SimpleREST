package nickrak.simplerest;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public final class RestServer
{
    public static void main(String[] args)
    {
        final Gettable g = new Gettable()
        {
            @Override
            public String RestRequest_GET(Map<String, String> args)
            {
                return "<html><body>Hello World!<br /><form action=\"index.rest\" method=\"post\"><input type=\"submit\" value=\"submit\" /><input=\"text\" id=\"durp\"/></form></body></html>";
            }
        };
        
        final Gettable g2 = new Gettable()
        {
            @Override
            public String RestRequest_GET(Map<String, String> args)
            {
                System.out.println("here");
                final StringBuilder sb = new StringBuilder();
                sb.append("<html><body><table>");
                for (String k : args.keySet())
                {
                    sb.append("<tr><td>");
                    sb.append(k);
                    sb.append("</td><td>");
                    sb.append(args.get(k));
                    sb.append("</td></tr>");
                }
                sb.append("</table></body></html>");
                
                return sb.toString();
            }
        };

        final Postable p = new Postable()
        {
            @Override
            public String RestRequest_POST(Map<String, String> args)
            {
                return "Hello Poster!";
            }
        };

        try
        {
            RestServer.AttachGet(54321, "/index.rest", g);
            RestServer.AttachPost(54321, "/index.rest", p);
            RestServer.AttachPost(54321, "/favicon.ico", p);
            RestServer.AttachGet(54321, "/favicon.ico", g);
            RestServer.AttachGet(54321, "/t", g2);
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        System.out.println("Ready");
        try
        {
            Thread.sleep(100000000);
        }
        catch (InterruptedException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private final static ConcurrentHashMap<Integer, RestServer> servers;

    static
    {
        servers = new ConcurrentHashMap<Integer, RestServer>();
    }

    private static void CreateServerIfUnavailable(int port) throws IOException
    {
        if (!servers.containsKey(port))
        {
            servers.put(port, new RestServer(port));
        }
    }

    public static void DestroyServerIfEmpty(int port)
    {
        if (servers.containsKey(port))
        {
            if (servers.get(port).getters.isEmpty() && servers.get(port).posters.isEmpty())
            {
                servers.get(port).destroy();
                servers.remove(port);
            }
        }
    }

    public static void AttachGet(int port, String path, Gettable getter) throws IOException
    {
        CreateServerIfUnavailable(port);
        servers.get(port).getters.put(path, getter);
    }

    public static void AttachPost(int port, String path, Postable poster) throws IOException
    {
        CreateServerIfUnavailable(port);
        servers.get(port).posters.put(path, poster);
    }

    public static void DetatchGet(int port, String path)
    {
        if (servers.containsKey(port))
        {
            servers.get(port).getters.remove(path);
            DestroyServerIfEmpty(port);
        }
    }

    public static void DetatchPost(int port, String path)
    {
        if (servers.containsKey(port))
        {
            servers.get(port).posters.remove(path);
            DestroyServerIfEmpty(port);
        }
    }

    private final ServerSocket socketserver;
    private final ConcurrentHashMap<String, Gettable> getters;
    private final ConcurrentHashMap<String, Postable> posters;
    private final RestListener listener;

    private RestServer(int port) throws IOException
    {
        this.socketserver = new ServerSocket(port);
        this.getters = new ConcurrentHashMap<String, Gettable>();
        this.posters = new ConcurrentHashMap<String, Postable>();
        this.listener = new RestListener(this.socketserver);
    }

    private final void destroy()
    {
        this.listener.destroy();
    }

    private final class RestListener implements Runnable
    {
        private final ServerSocket socket;
        private volatile boolean keepAlive;
        private final Thread t;

        public RestListener(ServerSocket sock)
        {
            this.socket = sock;
            this.keepAlive = true;
            this.t = new Thread(this);
            this.t.start();
        }

        public void destroy()
        {
            this.keepAlive = false;
            try
            {
                this.t.join();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        public void run()
        {
            try
            {
                this.socket.setSoTimeout(1000);
            }
            catch (SocketException se)
            {
            }
            while (this.keepAlive)
            {
                try
                {
                    final Socket cli = this.socket.accept();
                    new RestThread(cli);
                }
                catch (final IOException ioe)
                {
                }
            }
        }
    }

    private final class RestThread implements Runnable
    {
        private final class LinedStringBuilder
        {
            private final StringBuilder sb = new StringBuilder();

            public String toString()
            {
                return this.sb.toString();
            }

            public void writeLine(String line)
            {
                this.sb.append(line + "\n");
            }
        }

        public RestThread(Socket sock)
        {
            this.sock = sock;
            new Thread(this).start();
        }

        private final void generateHeader(final PrintWriter pw, String result, boolean ok)
        {
            final LinedStringBuilder sb = new LinedStringBuilder();
            sb.writeLine("HTTP/1.0 " + (ok ? "200 OK" : "404 Not Found"));
            sb.writeLine("Content-Type: text/html; charset=UTF-8");
            sb.writeLine("Content-Length: " + result.length());
            sb.writeLine("");
            sb.writeLine(result);
            sb.writeLine("");

            pw.write(sb.toString());
        }

        private final Socket sock;

        public final void run()
        {
            Scanner scan;
            try
            {
                scan = new Scanner(sock.getInputStream());

                final OutputStream os = sock.getOutputStream();
                final String req = scan.nextLine();

                for (String line = " "; !line.equals(""); line = scan.nextLine());

                final String[] parts = req.split(" ");
                String path = parts[1];
                final String verb = parts[0].toUpperCase();

                if (!verb.equals("GET") && !verb.equals("POST"))
                {
                    scan.close();
                    os.close();
                    sock.close();
                    return;
                }

                if (verb.equals("GET"))
                {
                    final ConcurrentHashMap<String, String> argz = new ConcurrentHashMap<String, String>();
                    if (path.contains("?"))
                    {
                        final String[] args = path.split("/?")[1].split("&");
                        path = path.split("/?")[0];

                        for (String arg : args)
                        {
                            final String[] half = arg.split("=");
                            if (half.length == 2)
                            {
                                argz.put(half[0], half[1]);
                            }
                        }
                    }

                    System.out.println("GET " + path);

                    final PrintWriter pw = new PrintWriter(os, true);
                    try
                    {
                        final String result = getters.get(path).RestRequest_GET(argz) + "\n";
                        this.generateHeader(pw, result, true);
                    }
                    catch (final Exception e)
                    {
                        final String result = e.getMessage() + "\n" + e.getStackTrace()[0].toString() + "\n" + path;
                        this.generateHeader(pw, result, false);
                    }

                    pw.flush();
                    pw.close();
                    this.sock.close();
                }

                if (verb.equals("POST"))
                {
                    final StringBuilder sb = new StringBuilder();
                    
                    for (;;)
                    {
                        final String line = scan.nextLine();
                        if (line.equals(""))
                        {
                            break;
                        }
                        else
                        {
                            sb.append(line);
                        }
                    }

                    System.out.println("POST :: " + path);

                    final ConcurrentHashMap<String, String> argz = new ConcurrentHashMap<String, String>();
                    final String[] args = sb.toString().split("&");

                    for (String arg : args)
                    {
                        final String[] half = arg.split("=");
                        if (half.length == 2)
                        {
                            argz.put(half[0], half[1]);
                        }
                    }

                    final PrintWriter pw = new PrintWriter(os, true);
                    try
                    {
                        final String result = posters.get(path).RestRequest_POST(argz);
                        this.generateHeader(pw, result, true);
                    }
                    catch (Exception e)
                    {
                        final String result = "404 Not Found";
                        this.generateHeader(pw, result, false);
                    }

                    pw.flush();
                    pw.close();
                    this.sock.close();

                }
            }
            catch (IOException e)
            {
            }
        }
    }
}
