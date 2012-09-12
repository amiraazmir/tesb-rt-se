/*
 * #%L
 * Talend :: ESB :: LOCATOR :: AUTH
 * %%
 * Copyright (C) 2011 - 2012 Talend Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.talend.esb.locator.server.auth;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.ArrayList;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.server.ServerCnxn;
import org.apache.zookeeper.server.auth.AuthenticationProvider;

public class SLAuthenticationProvider implements AuthenticationProvider {

    public static String SL_READ = "SL_READ";

    public static String SL_MAINTAIN = "SL_MAINTAIN";

    public static String SL_ADMIN = "SL_ADMIN";

    private Charset utf8CharSet;

    public SLAuthenticationProvider() {
        utf8CharSet = Charset.forName("UTF-8");
    }

    @Override
    public String getScheme() {
        return "sl";
    }

    private ArrayList<String> getUserRoles(final String user,
            final String password) throws LoginException {
        ArrayList<String> roles = new ArrayList<String>();
        LoginContext ctx = new LoginContext("karaf", new CallbackHandler() {
            @Override
            public void handle(Callback[] callbacks) throws IOException,
                    UnsupportedCallbackException {
                for (int i = 0; i < callbacks.length; i++) {
                    if (callbacks[i] instanceof NameCallback) {
                        ((NameCallback) callbacks[i]).setName(user);
                    } else if (callbacks[i] instanceof PasswordCallback) {
                        ((PasswordCallback) callbacks[i]).setPassword((password
                                .toCharArray()));
                    } else {
                        throw new UnsupportedCallbackException(callbacks[i]);
                    }
                }
            }
        });
        ctx.login();
        Subject subject = ctx.getSubject();
        for (Principal p : subject.getPrincipals()) {
            if (SL_READ.equals(p.getName().toUpperCase())
                    || SL_MAINTAIN.equals(p.getName().toUpperCase())
                    || SL_ADMIN.equals(p.getName().toUpperCase())) {
                roles.add(p.getName().toUpperCase());
            }
        }
        return roles;
    }

    @Override
    public Code handleAuthentication(ServerCnxn cnxn, byte[] authData) {
        String id = new String(authData, utf8CharSet);
        String userInfo[] = id.split(":");
        String user = "";
        String password = "";
        StringBuilder roles = new StringBuilder("");
        if (userInfo.length >= 1) {
            user = userInfo[0];
            if (userInfo.length >= 2) {
                password = userInfo[1];
            }
            try {
                ArrayList<String> rolesList = getUserRoles(user, password);
                for (int i = 0; i < rolesList.size(); i++) {
                    roles.append(rolesList.get(i));
                    if (i < rolesList.size() - 1)
                        roles.append(",");
                }
                if (rolesList.size() >= 1) {
                    cnxn.getAuthInfo().add(
                            new Id(getScheme(), roles.toString()));
                    return KeeperException.Code.OK;
                }
            } catch (LoginException e) {
                return KeeperException.Code.AUTHFAILED;
            }
        }
        return KeeperException.Code.AUTHFAILED;
    }

    @Override
    public boolean matches(String id, String aclExpr) {
        String[] roles = id.split(",");
        for (int i = 0; i < roles.length; i++) {
            if (roles[i].equals(aclExpr))
                return true;
        }
        return false;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public boolean isValid(String id) {
        return id.length() > 0;
    }
}
