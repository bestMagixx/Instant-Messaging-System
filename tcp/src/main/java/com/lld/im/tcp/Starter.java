package com.lld.im.tcp;


import com.lld.im.tcp.receiver.MessageReceiver;
import com.lld.im.tcp.redis.RedisManager;
import com.lld.im.tcp.register.RegistryZK;
import com.lld.im.tcp.register.ZKit;
import com.lld.im.tcp.server.LimServer;
import com.lld.im.tcp.server.LimWebSocketServer;
import com.lld.im.tcp.utils.MqFactory;
import org.I0Itec.zkclient.ZkClient;
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
        ZkClient zkClient = new ZkClient(config.getLim().getZkConfig().getZkAddr(),
                config.getLim().getZkConfig().getZkConnectTimeOut());
        ZKit zKit = new ZKit(zkClient);
        RegistryZK registryZK = new RegistryZK(zKit, hostAddress, config.getLim());
        Thread thread = new Thread(registryZK);
        thread.start();

    }

}
