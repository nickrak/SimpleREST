package nickrak.simplerest;

import java.util.Map;

public interface Postable
{
    public abstract String RestRequest_POST(Map<String, String> args);
}
