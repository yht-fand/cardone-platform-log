package top.cardone.log.service.impl;

import top.cardone.log.dao.OperateLogDao;
import org.springframework.transaction.annotation.Transactional;
import top.cardone.data.service.SimpleDefaultService;

/**
 * 操作日志服务
 *
 * @author yao hai tao
 */
@Transactional(readOnly = true)
public class OperateLogServiceImpl extends SimpleDefaultService<OperateLogDao> implements top.cardone.log.service.OperateLogService {
}