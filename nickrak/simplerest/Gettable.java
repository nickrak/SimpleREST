package nickrak.simplerest;

import java.util.Map;

public interface Gettable
{
    public abstract String RestRequest_GET(Map<String, String> args);
}
