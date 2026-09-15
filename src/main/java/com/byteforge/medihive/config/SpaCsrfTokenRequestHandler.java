package com.byteforge.medihive.config;
import jakarta.servlet.http.*;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.*;
/** Accept the plain token from the cookie in fetch headers, and masked tokens in login forms. */
public class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
 private final CsrfTokenRequestAttributeHandler plain=new CsrfTokenRequestAttributeHandler();
 private final XorCsrfTokenRequestAttributeHandler masked=new XorCsrfTokenRequestAttributeHandler();
 @Override public void handle(HttpServletRequest request,HttpServletResponse response,Supplier<CsrfToken> token){masked.handle(request,response,token);token.get().getToken();}
 @Override public String resolveCsrfTokenValue(HttpServletRequest request,CsrfToken token){return request.getHeader(token.getHeaderName())!=null?plain.resolveCsrfTokenValue(request,token):masked.resolveCsrfTokenValue(request,token);}
}
