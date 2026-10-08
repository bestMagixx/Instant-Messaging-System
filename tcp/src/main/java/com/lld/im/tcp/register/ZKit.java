package com.lld.im.tcp.register;


import com.lld.im.common.constant.Constants;
import org.I0Itec.zkclient.ZkClient;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.ExistsBuilder;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;


public class ZKit {

//    private ZkClient zkClient;
//
//    public ZKit(ZkClient zkClient) {
//        this.zkClient = zkClient;
//    }

//    //im-coreRoot/tcp/ip:port
//    public void createRootNode(){
//        boolean exists = zkClient.exists(Constants.ImCoreZkRoot);
//        if(!exists){
//            zkClient.createPersistent(Constants.ImCoreZkRoot);
//        }
//
//        boolean tcpExists = zkClient.exists(Constants.ImCoreZkRoot +
//                Constants.ImCoreZkRootTcp);
//        if(!tcpExists){
//            zkClient.createPersistent(Constants.ImCoreZkRoot +
//                    Constants.ImCoreZkRootTcp);
//        }
//
//        boolean webExists = zkClient.exists(Constants.ImCoreZkRoot +
//                Constants.ImCoreZkRootWeb);
//        if(!webExists){
//            zkClient.createPersistent(Constants.ImCoreZkRoot +
//                    Constants.ImCoreZkRootWeb);
//        }
//
//    }
//
//    //ip+prt
//    public void createNode(String path){
//        if(!zkClient.exists(path)){
//            zkClient.createEphemeral(path);
//        }
//    }
    public static ZKit zKit;

    private CuratorFramework curator;

    public ZKit(CuratorFramework curator) {
        this.curator = curator;
    }


    //im-coreRoot/tcp/ip:port
    public void createRootNode() throws Exception {
        ExistsBuilder existsBuilder = curator.checkExists();
        String errorString = "createRootTcpNode error";
        try {
            Stat tcpExists = curator.checkExists().forPath(Constants.ImCoreZkRootTcp);
            if(tcpExists == null){
                curator.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(Constants.ImCoreZkRootTcp);
            }
            errorString = "createRootWebNode error";
            Stat webExists = curator.checkExists().forPath(Constants.ImCoreZkRootWeb);
            if(webExists == null){
                curator.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(Constants.ImCoreZkRootWeb);
            }
        } catch (Exception e) {
            throw new Exception(errorString,e);
        }
    }

    //ip+prt
    public void createNode(String path) throws Exception {
        try {
            if(curator.checkExists().forPath(path) == null){
                curator.create().creatingParentsIfNeeded()
                        .withMode(CreateMode.EPHEMERAL).forPath(path);
            }else{
                throw new org.apache.zookeeper.KeeperException.NodeExistsException();
            }
        } catch (org.apache.zookeeper.KeeperException.NodeExistsException nodeExistsException) {
            throw new Exception("node exists", nodeExistsException);
        } catch (Exception e) {
            throw new Exception("createNode"+ path +"error",e);
        }
    }

    public void deleteNode(String path) throws Exception {
        try {
            curator.delete().forPath(path);
        } catch (org.apache.zookeeper.KeeperException.NoNodeException ignore) {
            // 节点可能已因会话过期被 ZK 删掉，忽略
        } catch (Exception e) {
            throw new Exception("deleteNode"+ path +"error", e);
        }
    }

    public void close(){
        curator.close();
    }
}
