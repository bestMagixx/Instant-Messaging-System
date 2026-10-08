package com.lld.im.tcp.register;

import com.lld.im.codec.config.BootstrapConfig;
import com.lld.im.common.constant.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnRegistryZK implements Runnable{

    private static Logger logger = LoggerFactory.getLogger(UnRegistryZK.class);

    private static ZKit zKit;

    private  String ip;

    private BootstrapConfig.TcpConfig tcpConfig;

    public UnRegistryZK(ZKit zKit, String ip, BootstrapConfig.TcpConfig tcpConfig) {
        UnRegistryZK.zKit = zKit;
        this.ip = ip;
        this.tcpConfig = tcpConfig;
    }

    @Override
    public void run() {
        try {
            zKit.deleteNode(Constants.ImCoreZkRootTcp + "/" + ip + ":" + tcpConfig.getTcpPort());
            zKit.deleteNode(Constants.ImCoreZkRootWeb + "/" + ip + ":" + tcpConfig.getWebSocketPort());
        } catch (Exception e) {
            logger.error("UnRegistry zookeeper error, msg=[{}]", e.getMessage());
        }finally {
            zKit.close();
        }
    }
}