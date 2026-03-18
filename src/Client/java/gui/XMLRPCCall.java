package gui;

import util.XmlRpcProxy;

public class XMLRPCCall {


    public static Object execute(String url, String method, Object[] send) {
        return XmlRpcProxy.execute(url, method, send);
    }
}
