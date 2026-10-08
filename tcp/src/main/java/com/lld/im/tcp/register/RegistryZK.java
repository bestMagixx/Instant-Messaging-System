package com.lld.im.tcp.register;

import com.lld.im.codec.config.BootstrapConfig;
import com.lld.im.common.constant.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ：lld
 * @version ：1.0
 *  */
public class RegistryZK implements Runnable{

    private static Logger logger = LoggerFactory.getLogger(RegistryZK.class);

    private static ZKit zKit;

    private  String ip;

    private BootstrapConfig.TcpConfig tcpConfig;

    public RegistryZK(ZKit zKit, String ip, BootstrapConfig.TcpConfig tcpConfig) {
        RegistryZK.zKit = zKit;
        this.ip = ip;
        this.tcpConfig = tcpConfig;
    }

    @Override
    public void run() {

        try {
            zKit.createRootNode();
            String tcpPath = "/" + Constants.ImCoreZkRootTcp + "/" + ip + ":" + tcpConfig.getTcpPort();
            zKit.createNode(tcpPath);
            logger.info("Registry zookeeper tcpPath success, msg=[{}]",tcpPath);

            String webPath = "/" + Constants.ImCoreZkRootWeb + "/" + ip + ":" + tcpConfig.getWebSocketPort();
            zKit.createNode(webPath);
            logger.info("Registry zookeeper webPath success, msg=[{}]", webPath);
        } catch (Exception e) {
            logger.error("Registry zookeeper error, msg=[{}]", e.getMessage());
            throw new RuntimeException(e);
        }

    }
}
