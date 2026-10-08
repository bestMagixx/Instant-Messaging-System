package com.lld.im.tcp;


import com.lld.im.common.constant.Constants;
import com.lld.im.tcp.receiver.MessageReceiver;
import com.lld.im.tcp.redis.RedisManager;
import com.lld.im.tcp.register.RegistryZK;
import com.lld.im.tcp.register.UnRegistryZK;
import com.lld.im.tcp.register.ZKit;
import com.lld.im.tcp.server.LimServer;
import com.lld.im.tcp.server.LimWebSocketServer;
import com.lld.im.tcp.utils.MqFactory;
import org.I0Itec.zkclient.ZkClient;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.yaml.snakeyaml.Yaml;
import com.lld.im.codec.config.BootstrapConfig;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

//@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class Starter {


//  HTTP GET POST PUT DELETE 1.0 1.1 2.0
    // client IOS 安卓 pc(windows mac) web //支持json 也支持protobuf
    //appId
    //28 + imei + body
    //请求体（指令 版本 clientType 消息解析类型 imei长度 appId bodyLen）+ imei号 + 请求体
    //len + body
    public static void main(String[] args) throws FileNotFoundException {
        if (args.length > 0) {
            //System.setProperty("zookeeper.sasl.client", "false");
            start(args[0]);
        }else{
            System.out.println("args.length = " + args.length);
        }
    }

    private static void start(String path){
        try {
            Yaml yaml = new Yaml();
            InputStream inputStream = new FileInputStream(path);
            BootstrapConfig bootstrapConfig = yaml.loadAs(inputStream, BootstrapConfig.class);
            System.out.println(bootstrapConfig.toString());

            new LimServer(bootstrapConfig.getLim()).start();
            new LimWebSocketServer(bootstrapConfig.getLim()).start();

            RedisManager.init(bootstrapConfig);

            MqFactory.init(bootstrapConfig.getLim().getRabbitmq());

            MessageReceiver.init(bootstrapConfig.getLim().getBrokerId() + "");

            registerZK(bootstrapConfig);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(500);
        }
    }

    public static  void registerZK(BootstrapConfig config) throws UnknownHostException {
        String hostAddress = InetAddress.getLocalHost().getHostAddress();
        System.err.println(config.getLim().getZkConfig().getZkAddr());
//        ZkClient zkClient = new ZkClient(config.getLim().getZkConfig().getZkAddr(),
//                config.getLim().getZkConfig().getZkConnectTimeOut());
        RetryPolicy retry = new ExponentialBackoffRetry(1000, 3);
        CuratorFramework curator = CuratorFrameworkFactory.builder()
                .connectString(config.getLim().getZkConfig().getZkAddr())
                .namespace(Constants.ImCoreZkRoot)   // 之后所有路径都相对该根，等价于 /im-coreRoot
                .retryPolicy(retry)
                .sessionTimeoutMs(60_000)   // 会话超时，决定临时节点存活窗口
                .connectionTimeoutMs(15_000)
                .build();
//        ZKit zKit = new ZKit(zkClient);
        curator.start();
        ZKit.zKit = new ZKit(curator);
        RegistryZK registryZK = new RegistryZK(ZKit.zKit, hostAddress, config.getLim());
        Thread thread = new Thread(registryZK);
        curator.getConnectionStateListenable().addListener((c, state) -> {
            if (state == ConnectionState.RECONNECTED) {
                try {
                    thread.start();
                }
                catch (Exception e) {
                    System.out.println("Session reconnected, but failed");
                }
            }
        });

        // 关机钩子：主动删节点 + 关会话，service 侧 TreeCache 立刻感知下线
        Runtime.getRuntime().addShutdownHook(new Thread(new UnRegistryZK(ZKit.zKit, hostAddress, config.getLim())));
    }

}
