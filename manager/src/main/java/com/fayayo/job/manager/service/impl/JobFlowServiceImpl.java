package com.fayayo.job.manager.service.impl;

import com.fayayo.job.common.constants.Constants;
import com.fayayo.job.common.enums.CycleEnums;
import com.fayayo.job.common.enums.FlowStatusEnums;
import com.fayayo.job.common.enums.PriorityEnums;
import com.fayayo.job.common.enums.ResultEnum;
import com.fayayo.job.common.exception.CommonException;
import com.fayayo.job.common.util.DateTimeUtil;
import com.fayayo.job.common.util.EnumUtil;
import com.fayayo.job.common.util.KeyUtil;
import com.fayayo.job.entity.JobFlow;
import com.fayayo.job.entity.JobInfo;
import com.fayayo.job.entity.params.JobFlowParams;
import com.fayayo.job.manager.repository.JobFlowRepository;
import com.fayayo.job.manager.service.JobFlowService;
import com.fayayo.job.manager.service.JobInfoService;
import com.fayayo.job.manager.vo.JobFlowVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author dalizu on 2018/11/3.
 * @version v1.0
 * @desc
 */
@Slf4j
@Service
public class JobFlowServiceImpl implements JobFlowService {


    @Autowired
    private JobFlowRepository jobFlowRepository;

    @Autowired
    private JobInfoService jobInfoService;

    @Autowired
    private JobSchedulerCore jobSchedulerCore;


    @Override
    public JobFlow saveOrUpdate(JobFlowParams jobFlowParams) {
        JobFlow jobFlow = new JobFlow();
        String id = jobFlowParams.getId();
        if (StringUtils.isNotBlank(id)) {//修改

            jobFlow = findOne(id);

            BeanUtils.copyProperties(jobFlowParams, jobFlow);
            if(jobFlow.getJobCycle().equals(CycleEnums.ONE.getCode())){
                jobFlow.setJobCycleValue(0);
            }
            jobFlowRepository.save(jobFlow);

        } else {//新增
            String keyId = KeyUtil.genUniqueKey();
            BeanUtils.copyProperties(jobFlowParams, jobFlow);
            jobFlow.setId(keyId);
            jobFlow.setFlowStatus(FlowStatusEnums.OFF_LINE.getCode());//设置状态,默认没有上线
            if(jobFlow.getJobCycle().equals(CycleEnums.ONE.getCode())){
                jobFlow.setJobCycleValue(0);
            }
            jobFlowRepository.save(jobFlow);
        }
        return jobFlow;
    }

    @Override
    public JobFlow findOne(String jobFlowId) {

        JobFlow jobFlow = jobFlowRepository.findById(jobFlowId).orElse(null);


        log.debug("{}查询单个任务执行器信息, 结果:{}", Constants.LOG_PREFIX, jobFlow);

        return jobFlow;
    }

    @Override
    public JobFlowVo findById(String jobFlowId) {

        JobFlow jobFlow = findOne(jobFlowId);

        JobFlowVo jobFlowVo = new JobFlowVo();
        BeanUtils.copyProperties(jobFlow, jobFlowVo);
        jobFlowVo.setStartAtStr(DateTimeUtil.dateToStr(jobFlow.getStartAt()));

        log.debug("{}查询单个任务执行器信息, 结果:{}", Constants.LOG_PREFIX, jobFlowVo);

        return jobFlowVo;
    }

    @Override
    public Page<JobFlowVo> query(Pageable pageable) {
        Page<JobFlow> page = jobFlowRepository.findAll(pageable);

        List<JobFlow> sortList = page.getContent();//因为是不可修改的，所以我们要再次转换

        List<JobFlowVo> listBody = new ArrayList<>();
        if (!CollectionUtils.isEmpty(sortList)) {
            sortList.stream().map(f -> {
                JobFlowVo jobFlowVo = new JobFlowVo();
                BeanUtils.copyProperties(f, jobFlowVo);
                jobFlowVo.setJobCycleDesc(EnumUtil.getByCode(f.getJobCycle(), CycleEnums.class).getMessage());
                jobFlowVo.setFlowPriorityDesc(EnumUtil.getByCode(f.getFlowPriority(), PriorityEnums.class).getMessage());
                return listBody.add(jobFlowVo);

            }).collect(Collectors.toList());

            //按照seq字段排序 seq从小到大
            Collections.sort(listBody, new Comparator<JobFlowVo>() {
                @Override
                public int compare(JobFlowVo o1, JobFlowVo o2) {
                    return o1.getSeq() - o2.getSeq();
                }
            });
        }

        return new PageImpl<>(listBody, pageable, page.getTotalElements());
    }

    @Override
    public JobFlow findByName(String name) {
        return jobFlowRepository.findByName(name);
    }

    @Override
    public void deleteById(String id) {

        //先删除任务流下面的任务

        List<JobInfo> jobInfoList = jobInfoService.findByJobFlow(id);
        if (!CollectionUtils.isEmpty(jobInfoList)) {
            throw new CommonException(ResultEnum.JOB_FLOW_HAVE_JOB);
        }
        jobFlowRepository.deleteById(id);
    }


    @Override
    public void upOrDown(String id, Integer status) {
        //获取任务流的信息
        JobFlow jobFlow = findOne(id);

        //获取执行的周期信息
        Integer cycle = jobFlow.getJobCycle();
        Integer value = jobFlow.getJobCycleValue();

        List<JobInfo> jobInfoList = jobInfoService.findByJobFlow(id);
        if (CollectionUtils.isEmpty(jobInfoList)) {
            throw new CommonException(ResultEnum.JOB_FLOW_NOT_HAVE_JOB);
        }
        //TODO 获取任务流下面的任务,暂时只获取一个。
        JobInfo jobInfo = jobInfoList.get(0);

        if (FlowStatusEnums.ON_LINE.getCode() == status) {//现在是上线状态，要改为下线
            //停止调度
            jobSchedulerCore.removeFlowJob(jobInfo.getId(), String.valueOf(jobInfo.getJobGroup()));
            jobFlow.setFlowStatus(FlowStatusEnums.OFF_LINE.getCode());

        } else {
            //开始调度
            jobSchedulerCore.addFlowJob(jobInfo.getId(), String.valueOf(jobInfo.getJobGroup()), jobFlow.getStartAt(), cycle, value);
            jobFlow.setFlowStatus(FlowStatusEnums.ON_LINE.getCode());
        }

        jobFlowRepository.save(jobFlow);
    }


    public static void main(String[] args) {
        System.out.println(StringUtils.isNotBlank("x"));
        System.out.println(StringUtils.isNoneBlank(""));
        System.out.println(StringUtils.isNotEmpty(""));
        System.out.println(StringUtils.isNoneEmpty(""));
    }

}
