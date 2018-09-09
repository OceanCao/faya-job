package com.fayayo.job.manager.core.cluster.support;

import com.fayayo.job.common.constants.Constants;
import com.fayayo.job.common.enums.ResultEnum;
import com.fayayo.job.common.exception.CommonException;
import com.fayayo.job.common.params.JobInfoParam;
import com.fayayo.job.common.util.EnumUtil;
import com.fayayo.job.core.extension.ExtensionLoader;
import com.fayayo.job.core.spi.ServiceDiscovery;
import com.fayayo.job.core.spi.impl.ZkServiceDiscovery;
import com.fayayo.job.core.zookeeper.ZKCuratorClient;
import com.fayayo.job.core.zookeeper.ZkProperties;
import com.fayayo.job.manager.config.SpringHelper;
import com.fayayo.job.manager.core.cluster.HaStrategy;
import com.fayayo.job.manager.core.cluster.LoadBalance;
import com.fayayo.job.manager.core.cluster.ha.HaStrategyEnums;
import com.fayayo.job.manager.core.route.JobRouteExchange;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author dalizu on 2018/8/17.
 * @version v1.0
 * @desc 构造cluster
 */
@Slf4j
public class ClusterSupport {

    /**
     * @描述 构造cluster
     */
    public Cluster buildClusterSupport(JobInfoParam jobInfo) {
        //获取ha的实现
        Integer haCode = jobInfo.getJobHa();
        String haStrategyName= EnumUtil.getByCode(haCode, HaStrategyEnums.class).getDesc()==null
                ?"failover":EnumUtil.getByCode(haCode, HaStrategyEnums.class).getDesc();//默认值
        HaStrategy haStrategy = ExtensionLoader.getExtensionLoader(HaStrategy.class).getExtension(haStrategyName);
        //获取loadbalance的实现
        LoadBalance loadBalance = getLoadBalance(jobInfo);

        Cluster cluster = new ClusterSpi(haStrategy, loadBalance);
        return cluster;
    }

    /**
     * @描述 获取负载策略
     */
    public LoadBalance getLoadBalance(JobInfoParam jobInfoParam) {

        //从zk中获取 服务ip地址列表
        ZKCuratorClient zkCuratorClient = SpringHelper.popBean(ZKCuratorClient.class);
        ZkProperties zkProperties = SpringHelper.popBean(ZkProperties.class);

        ServiceDiscovery serviceDiscovery = new ZkServiceDiscovery(zkCuratorClient, zkProperties);

        List<String> list = serviceDiscovery.discover(jobInfoParam.getJobGroupName());//根据所属执行器查询ip地址
        if (CollectionUtils.isEmpty(list)) {
            throw new CommonException(ResultEnum.JOB_NOT_FIND_ADDRESS);
        }
        //获取具体的ip
        List<String> addressList = list.stream().map(e -> {
            return zkCuratorClient.getData(e);
        }).collect(Collectors.toList());
        log.info("{}获取服务地址列表,jobid:{},执行器名称:{},addressList:{}",Constants.LOG_PREFIX,jobInfoParam.getId(), jobInfoParam.getJobGroupName(), StringUtils.join(addressList, ","));

        //获取负载均衡的策略  +  Ha策略  然后对选择的机器发送请求任务
        JobRouteExchange jobRouteExchange = new JobRouteExchange(addressList);
        LoadBalance loadBalance = jobRouteExchange.getLoadBalance(jobInfoParam);
        log.info("{}服务执行负载策略:{}", Constants.LOG_PREFIX,loadBalance.getClass().getName());
        return loadBalance;
    }
}
