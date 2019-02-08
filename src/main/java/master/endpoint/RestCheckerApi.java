package master.endpoint;

import java.util.List;

public interface RestCheckerApi {
    List<JavaEndpoint> getEndpointsFromDir();
}
