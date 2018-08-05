package com.fayayo.job.core.spi;

/**
 * @author dalizu on 2018/8/4.
 * @version v1.0
 * @desc
 */
public interface ServiceRegistry {



    /**

     *@描述 服务注册

     *@参数  服务名称，服务地址

     *@返回值  void

     *@创建人  dalizu

     *@创建时间  2018/8/4

     */
    void register(String serviceName, String serviceAddress);



}
