package tokenrest;

import javax.ws.rs.container.*;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.core.Response;
import java.io.IOException;

@Provider
public class CORSFilter implements ContainerResponseFilter {

    @Override
    public void filter(
            ContainerRequestContext requestContext,
            ContainerResponseContext responseContext
    ) throws IOException {

        responseContext.getHeaders().add(
                "Access-Control-Allow-Origin", "*"
        );

        responseContext.getHeaders().add(
                "Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS"
        );

        responseContext.getHeaders().add(
                "Access-Control-Allow-Headers",
                "Content-Type, X-Doc-Id, X-File-Name, X-Doc-Type, p_app_id, p_session_id"
        );

        responseContext.getHeaders().add(
                "Access-Control-Max-Age", "3600"
        );
    }
}
