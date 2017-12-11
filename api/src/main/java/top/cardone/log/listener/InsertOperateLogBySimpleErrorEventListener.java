package top.cardone.log.listener;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.postgresql.util.PGobject;
import org.springframework.context.ApplicationListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.support.TaskUtils;
import top.cardone.context.ApplicationContextHolder;
import top.cardone.context.event.SimpleErrorEvent;
import top.cardone.context.util.MapUtils;
import top.cardone.core.util.func.Func0;
import top.cardone.core.util.func.Func1;
import top.cardone.log.service.OperateLogService;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Created by cardo on 2017/7/18 0018.
 */
@Log4j2
public class InsertOperateLogBySimpleErrorEventListener implements ApplicationListener<SimpleErrorEvent> {
    @Setter
    private boolean skipCreatedByCodeBlank = true;

    @Setter
    private Map<String, String> typeCodeMap;

    @Setter
    private String findListDictionaryFuncName = "findListDictionaryFunc";

    @Setter
    private Map<String, Object> findListDictionaryMap;

    public InsertOperateLogBySimpleErrorEventListener() {
        typeCodeMap = Maps.newHashMap();

        typeCodeMap.put("insert*", "insert");
        typeCodeMap.put("update*", "update");
        typeCodeMap.put("delete*", "delete");
        typeCodeMap.put("save*", "save");
        typeCodeMap.put("page*", "page");
        typeCodeMap.put("find*", "find");
        typeCodeMap.put("read*", "read");

        findListDictionaryMap = Maps.newHashMap();

        findListDictionaryMap.put("dictionaryTypeCode", "serviceName");

        findListDictionaryMap.put("stateCode", "1");
        findListDictionaryMap.put("dataStateCode", "1");
    }

    private String getMessage(String className) {
        List<Map<String, Object>> serviceNameList = (List<Map<String, Object>>) ApplicationContextHolder.getBean(Func1.class, this.findListDictionaryFuncName).func(this.findListDictionaryMap);

        if (CollectionUtils.isEmpty(serviceNameList)) {
            return StringUtils.EMPTY;
        }

        for (Map<String, Object> serviceName : serviceNameList) {
            String dictionaryCode = MapUtils.getString(serviceName, "dictionary_code");

            if (StringUtils.isNotBlank(top.cardone.context.util.StringUtils.getPathForMatch(Lists.newArrayList(dictionaryCode), className))) {
                return MapUtils.getString(serviceName, "name");
            }
        }

        return StringUtils.EMPTY;
    }

    @Override
    public void onApplicationEvent(SimpleErrorEvent simpleErrorEvent) {
        String createdByCode = ApplicationContextHolder.func(Func0.class, func -> (String) func.func(), "readPrincipalFunc");

        if (skipCreatedByCodeBlank && StringUtils.isBlank(createdByCode)) {
            return;
        }

        String message = this.getMessage(simpleErrorEvent.getFlags()[0]);

        if (StringUtils.isBlank(message)) {
            return;
        }

        ApplicationContextHolder.getBean(TaskExecutor.class).execute(TaskUtils.decorateTaskWithErrorHandler(() -> {
            Map<String, Object> insert = Maps.newHashMap();

            String typeCode = StringUtils.defaultString(top.cardone.context.util.StringUtils.getPathForMatch(typeCodeMap.keySet(), simpleErrorEvent.getFlags()[1]), "other");

            insert.put("typeCode", typeCode);
            insert.put("createdByCode", createdByCode);
            insert.put("personalCode", createdByCode);
            insert.put("objectTypeCode", "userLog");
            insert.put("objectCode", createdByCode);
            insert.put("message", message);

            Map<String, Object> jsonData = Maps.newHashMap();

            jsonData.put("flags", simpleErrorEvent.getFlags());

            if (ArrayUtils.isNotEmpty(simpleErrorEvent.getArgs())) {
                List<Object> newArgs = Lists.newArrayList();

                for (Object arg : simpleErrorEvent.getArgs()) {
                    if (arg == null) {
                        continue;
                    }

                    if (arg instanceof Class) {
                        newArgs.add(arg.toString());
                    } else if (arg instanceof Serializable) {
                        newArgs.add(arg);
                    } else {
                        newArgs.add(arg.toString());
                    }
                }

                if (CollectionUtils.isNotEmpty(newArgs)) {
                    jsonData.put("input", newArgs);
                }
            }

            jsonData.put("configs", simpleErrorEvent.getConfigs());
            jsonData.put("throwable", simpleErrorEvent.getThrowable());

            PGobject jsonObject = new PGobject();

            jsonObject.setType("json");

            try {
                jsonObject.setValue(ApplicationContextHolder.getBean(Gson.class).toJson(jsonData));

                insert.put("jsonData", jsonObject);
            } catch (Exception e) {
                log.error(e);
            }

            ApplicationContextHolder.getBean(OperateLogService.class).insert(insert);
        }, null, false));
    }
}
