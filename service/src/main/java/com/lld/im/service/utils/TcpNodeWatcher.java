package com.lld.im.service.utils;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.TreeCache;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TcpNodeWatcher {
    private final TreeCache treeCache;
    // 本地缓存，路由读它即可，ConcurrentHashMap 保证线程安全
    private final Set<String> liveNodes = ConcurrentHashMap.newKeySet();
 
    public TcpNodeWatcher(CuratorFramework client) {
        this.treeCache = TreeCache.newBuilder(client, "/tcp").build();
    }
 
    public void start() throws Exception {
        treeCache.getListenable().addListener((c, event) -> {
            ChildData data = event.getData();
            if (data == null) return;
            String path = data.getPath();
            switch (event.getType()) {
                case NODE_ADDED:
                    liveNodes.add(path); break;          // 网关上线
                case NODE_REMOVED:
                    liveNodes.remove(path); break;       // 网关下线/会话过期，秒级剔除
                default: break;
            }
        });
        treeCache.start();   // 会先拉全量快照触发一次 NODE_ADDED
    }
 
    public Set<String> getLiveNodes() {
        return liveNodes;     // service 登录路由直接调这个方法
    }
 
    public void stop() throws Exception { treeCache.close(); }
}
