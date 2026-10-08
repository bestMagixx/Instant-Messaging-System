package com.lld.im.service.utils;

import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * @author: Chackylee
 * @description: Zookeeper 工具
 **/
@Component
public class ZKit {

    private static Logger logger = LoggerFactory.getLogger(ZKit.class);

//    @Autowired
//    private ZkClient zkClient;
//    /**
//     * get all TCP server node from zookeeper
//     *
//     * @return
//     */
//    public List<String> getAllTcpNode() {
//        List<String> children = zkClient.getChildren(Constants.ImCoreZkRoot + Constants.ImCoreZkRootTcp);
////        logger.info("Query all node =[{}] success.", JSON.toJSONString(children));
//        return children;
//    }
//
//    /**
//     * get all WEB server node from zookeeper
//     *
//     * @return
//     */
//    public List<String> getAllWebNode() {
//        List<String> children = zkClient.getChildren(Constants.ImCoreZkRoot + Constants.ImCoreZkRootWeb);
////        logger.info("Query all node =[{}] success.", JSON.toJSONString(children));
//        return children;
//    }

    @Autowired
    private CuratorFramework curatorFramework;

    private TcpNodeWatcher tcpNodeWatcher;

    public ZKit() throws Exception {
        tcpNodeWatcher = new TcpNodeWatcher(curatorFramework);
        tcpNodeWatcher.start();
    }

    public Set<String> getAllTcpNode() {
        return tcpNodeWatcher.getLiveNodes();
    }

    public Set<String> getAllWebNode() {
        return tcpNodeWatcher.getLiveNodes();
    }

}
