package cn.iocoder.yudao.module.tool.service.codegen.inner;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.engine.velocity.VelocityEngine;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.tool.enums.codegen.CodegenSceneEnum;
import cn.iocoder.yudao.module.tool.framework.codegen.config.CodegenProperties;
import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;
import cn.iocoder.yudao.framework.excel.core.annotations.DictFormat;
import cn.iocoder.yudao.framework.excel.core.convert.DictConvert;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.operatelog.core.annotations.OperateLog;
import cn.iocoder.yudao.framework.operatelog.core.enums.OperateTypeEnum;
import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.QueryWrapperX;
import cn.iocoder.yudao.module.tool.dal.dataobject.codegen.CodegenColumnDO;
import cn.iocoder.yudao.module.tool.dal.dataobject.codegen.CodegenTableDO;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import com.google.common.collect.Maps;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.hutool.core.map.MapUtil.getStr;
import static cn.hutool.core.text.CharSequenceUtil.*;

/**
 * ????????????????????????????????????????????????
 * ???????????? {@link org.apache.velocity.app.Velocity} ??????????????????
 *
 * ????????? Java ?????????????????????????????????Freemarker???Velocity???Thymeleaf ??????????????????????????? hutool ????????? {@link cn.hutool.extra.template.Template} ??????
 *
 * @author ????????????
 */
@Component
public class CodegenEngine {

    /**
     * ????????????
     * key???????????? resources ?????????
     * value??????????????????
     */
    private static final Map<String, String> TEMPLATES = MapUtil.<String, String>builder(new LinkedHashMap<>()) // ??????
            // Java module-impl Main
            .put(javaTemplatePath("controller/vo/baseVO"), javaModuleImplVOFilePath("BaseVO"))
            .put(javaTemplatePath("controller/vo/createReqVO"), javaModuleImplVOFilePath("CreateReqVO"))
            .put(javaTemplatePath("controller/vo/pageReqVO"), javaModuleImplVOFilePath("PageReqVO"))
            .put(javaTemplatePath("controller/vo/respVO"), javaModuleImplVOFilePath("RespVO"))
            .put(javaTemplatePath("controller/vo/updateReqVO"), javaModuleImplVOFilePath("UpdateReqVO"))
            .put(javaTemplatePath("controller/vo/exportReqVO"), javaModuleImplVOFilePath("ExportReqVO"))
            .put(javaTemplatePath("controller/vo/excelVO"), javaModuleImplVOFilePath("ExcelVO"))
            .put(javaTemplatePath("controller/controller"), javaModuleImplControllerFilePath())
            .put(javaTemplatePath("convert/convert"),
                    javaModuleImplMainFilePath("convert/${table.businessName}/${table.className}Convert"))
            .put(javaTemplatePath("dal/do"),
                    javaModuleImplMainFilePath("dal/dataobject/${table.businessName}/${table.className}DO"))
            .put(javaTemplatePath("dal/mapper"),
                    javaModuleImplMainFilePath("dal/mysql/${table.businessName}/${table.className}Mapper"))
            .put(javaTemplatePath("service/serviceImpl"),
                    javaModuleImplMainFilePath("service/${table.businessName}/${table.className}ServiceImpl"))
            .put(javaTemplatePath("service/service"),
                    javaModuleImplMainFilePath("service/${table.businessName}/${table.className}Service"))
            // Java module-impl Test
            .put(javaTemplatePath("test/serviceTest"),
                    javaModuleImplTestFilePath("service/${table.businessName}/${table.className}ServiceImplTest"))
            // Java module-api Main
            .put(javaTemplatePath("enums/errorcode"), javaModuleApiMainFilePath("enums/ErrorCodeConstants_????????????"))
            // Vue
            .put(vueTemplatePath("views/index.vue"),
                    vueFilePath("views/${table.moduleName}/${classNameVar}/index.vue"))
            .put(vueTemplatePath("api/api.js"),
                    vueFilePath("api/${table.moduleName}/${classNameVar}.js"))
            // SQL
            .put("codegen/sql/sql.vm", "sql/sql.sql")
            .put("codegen/sql/h2.vm", "sql/h2.sql")
            .build();

    @Resource
    private CodegenProperties codegenProperties;

    /**
     * ?????????????????? hutool ??????
     */
    private final TemplateEngine templateEngine;
    /**
     * ????????????????????????
     */
    private final Map<String, Object> globalBindingMap = new HashMap<>();

    public CodegenEngine() {
        // ????????? TemplateEngine ??????
        TemplateConfig config = new TemplateConfig();
        config.setResourceMode(TemplateConfig.ResourceMode.CLASSPATH);
        this.templateEngine = new VelocityEngine(config);
    }

    @PostConstruct
    private void initGlobalBindingMap() {
        // ????????????
        globalBindingMap.put("basePackage", codegenProperties.getBasePackage());
        globalBindingMap.put("baseFrameworkPackage", codegenProperties.getBasePackage() + '.' + "framework"); // ?????????????????????????????? package ??????
        // ?????? Java Bean
        globalBindingMap.put("CommonResultClassName", CommonResult.class.getName());
        globalBindingMap.put("PageResultClassName", PageResult.class.getName());
        // VO ??????????????????
        globalBindingMap.put("PageParamClassName", PageParam.class.getName());
        globalBindingMap.put("DictFormatClassName", DictFormat.class.getName());
        // DO ??????????????????
        globalBindingMap.put("baseDOFields", CodegenBuilder.BASE_DO_FIELDS);
        globalBindingMap.put("QueryWrapperClassName", LambdaQueryWrapperX.class.getName());
        globalBindingMap.put("BaseMapperClassName", BaseMapperX.class.getName());
        // Util ?????????
        globalBindingMap.put("ServiceExceptionUtilClassName", ServiceExceptionUtil.class.getName());
        globalBindingMap.put("DateUtilsClassName", DateUtils.class.getName());
        globalBindingMap.put("ExcelUtilsClassName", ExcelUtils.class.getName());
        globalBindingMap.put("ObjectUtilsClassName", ObjectUtils.class.getName());
        globalBindingMap.put("DictConvertClassName", DictConvert.class.getName());
        globalBindingMap.put("OperateLogClassName", OperateLog.class.getName());
        globalBindingMap.put("OperateTypeEnumClassName", OperateTypeEnum.class.getName());
    }

    public Map<String, String> execute(CodegenTableDO table, List<CodegenColumnDO> columns) {
        // ?????? bindingMap
        Map<String, Object> bindingMap = new HashMap<>(globalBindingMap);
        bindingMap.put("table", table);
        bindingMap.put("columns", columns);
        bindingMap.put("primaryColumn", CollectionUtils.findFirst(columns, CodegenColumnDO::getPrimaryKey)); // ????????????
        bindingMap.put("sceneEnum", CodegenSceneEnum.valueOf(table.getScene()));

        // className ??????
        // ???????????????????????? TestDictType ????????? DictType. ????????? create ?????????????????????????????? Test ??????
        String simpleClassName = removePrefix(table.getClassName(), upperFirst(table.getModuleName()));
        bindingMap.put("simpleClassName", simpleClassName);
        bindingMap.put("simpleClassName_underlineCase", toUnderlineCase(simpleClassName)); // ??? DictType ????????? dict_type
        bindingMap.put("classNameVar", lowerFirst(simpleClassName)); // ??? DictType ????????? dictType???????????????
        // ??? DictType ????????? dict-type
        String simpleClassNameStrikeCase = toSymbolCase(simpleClassName, '-');
        bindingMap.put("simpleClassName_strikeCase", simpleClassNameStrikeCase);
        // permission ??????
        bindingMap.put("permissionPrefix", table.getModuleName() + ":" + simpleClassNameStrikeCase);

        // ????????????????????????????????? DB ????????????
        if (CollectionUtils.findFirst(columns, column -> column.getColumnName().equals(CodegenBuilder.TENANT_ID_FIELD)) != null) {
            bindingMap.put("BaseDOClassName", TenantBaseDO.class.getName());
            bindingMap.put("BaseDOClassName_simple", TenantBaseDO.class.getSimpleName());
        } else {
            bindingMap.put("BaseDOClassName", BaseDO.class.getName());
            bindingMap.put("BaseDOClassName_simple", BaseDO.class.getSimpleName());
        }

        // ????????????
        final Map<String, String> result = Maps.newLinkedHashMapWithExpectedSize(TEMPLATES.size()); // ??????
        TEMPLATES.forEach((vmPath, filePath) -> {
            filePath = formatFilePath(filePath, bindingMap);
            String content = templateEngine.getTemplate(vmPath).render(bindingMap);
            result.put(filePath, content);
        });
        return result;
    }

    private String formatFilePath(String filePath, Map<String, Object> bindingMap) {
        filePath = StrUtil.replace(filePath, "${basePackage}",
                getStr(bindingMap, "basePackage").replaceAll("\\.", "/"));
        filePath = StrUtil.replace(filePath, "${classNameVar}",
                getStr(bindingMap, "classNameVar"));
        // sceneEnum ???????????????
        CodegenSceneEnum sceneEnum = (CodegenSceneEnum) bindingMap.get("sceneEnum");
        filePath = StrUtil.replace(filePath, "${sceneEnum.prefixClass}", sceneEnum.getPrefixClass());
        filePath = StrUtil.replace(filePath, "${sceneEnum.basePackage}", sceneEnum.getBasePackage());
        // table ???????????????
        CodegenTableDO table = (CodegenTableDO) bindingMap.get("table");
        filePath = StrUtil.replace(filePath, "${table.moduleName}", table.getModuleName());
        filePath = StrUtil.replace(filePath, "${table.businessName}", table.getBusinessName());
        filePath = StrUtil.replace(filePath, "${table.className}", table.getClassName());
        return filePath;
    }

    private static String javaTemplatePath(String path) {
        return "codegen/java/" + path + ".vm";
    }

    private static String javaModuleImplVOFilePath(String path) {
        return javaModuleFilePath("controller/${sceneEnum.basePackage}/${table.businessName}/" +
                "vo/${sceneEnum.prefixClass}${table.className}" + path, "impl", "main");
    }

    private static String javaModuleImplControllerFilePath() {
        return javaModuleFilePath("controller/${sceneEnum.basePackage}/${table.businessName}/" +
                "${sceneEnum.prefixClass}${table.className}Controller", "impl", "main");
    }

    private static String javaModuleImplMainFilePath(String path) {
        return javaModuleFilePath(path, "impl", "main");
    }

    private static String javaModuleApiMainFilePath(String path) {
        return javaModuleFilePath(path, "api", "main");
    }

    private static String javaModuleImplTestFilePath(String path) {
        return javaModuleFilePath(path, "impl", "test");
    }

    private static String javaModuleFilePath(String path, String module, String src) {
        return "yudao-module-${table.moduleName}/" + // ????????????
                "yudao-module-${table.moduleName}-" + module + "/" + // ?????????
                "src/" + src + "/java/${basePackage}/module/${table.moduleName}/" + path + ".java";
    }

    private static String vueTemplatePath(String path) {
        return "codegen/vue/" + path + ".vm";
    }

    private static String vueFilePath(String path) {
        return "yudao-ui-${sceneEnum.basePackage}/" + // ????????????
                "src/" + path;
    }

}
