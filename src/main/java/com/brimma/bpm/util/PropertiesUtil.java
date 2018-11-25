package com.brimma.bpm.util;

/**
 * Not used now .. need to evaluate and remove
 */
public class PropertiesUtil {
    public static String getTransformationUrl(String transformer) {
        final String[] beanData = getBeanData(transformer);
        try {
            Class.forName(beanData[0]);
            return "class:" + beanData[0] + "?method="+beanData[1];
        } catch (ClassNotFoundException e) {
            return "bean:" + beanData[1] + "?method=handle(*, *)";
        }
    }

    private static String[] getBeanData(String url) {
        return (url==null || url.isEmpty())?new String[]{"com.brimma.integration.transformer.DefaultTransformer", "handle(*, *)"}:(
                url.contains(":")?url.split(":"):(
                        url.endsWith(".json")?new String[]{"com.brimma.integration.transformer.ElliePayloadTransformer", "handle(${body}, \""+url+"\")"}
                            :(url.endsWith(".xsl")?new String[]{"com.brimma.integration.transformer.XslTransformer", "handle(${body}, \""+url+"\")"}:new String[]{"com.brimma.integration.transformer.DefaultTransformer", "handle(*, *)"})
                        )
        );
    }
}
