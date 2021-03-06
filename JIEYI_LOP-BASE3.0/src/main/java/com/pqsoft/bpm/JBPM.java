package com.pqsoft.bpm;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.jbpm.api.ExecutionService;
import org.jbpm.api.HistoryService;
import org.jbpm.api.IdentityService;
import org.jbpm.api.ManagementService;
import org.jbpm.api.ProcessEngine;
import org.jbpm.api.ProcessInstance;
import org.jbpm.api.RepositoryService;
import org.jbpm.api.TaskService;
import org.jbpm.api.task.Task;
import org.jbpm.pvm.internal.stream.UrlStreamInput;

import com.pqsoft.base.interfaceTemplate.service.RunInterfaceTemplate;
import com.pqsoft.base.task.service.TaskClaimService;
import com.pqsoft.skyeye.Spring;
import com.pqsoft.skyeye.api.Dao;
import com.pqsoft.skyeye.rbac.api.Security;
import com.pqsoft.sms.service.WeixinService;
import com.pqsoft.util.StringUtils;

/**
 * 工作流基础工具
 * 
 * @author <a href="mailto:lichaohn@163.com">李超</a>
 */
public class JBPM {
	private static ProcessEngine processEngine = null;
	private static RepositoryService repositoryService = null;
	private static ExecutionService executionService = null;
	private static TaskService taskService = null;
	private static ManagementService managementService = null;
	private static HistoryService historyService = null;
	private static IdentityService identityService = null;
	static {
		processEngine = (ProcessEngine) Spring.getBean("processEngine");
		repositoryService = processEngine.getRepositoryService();
		executionService = processEngine.getExecutionService();
		taskService = processEngine.getTaskService();
		managementService = processEngine.getManagementService();
		historyService = processEngine.getHistoryService();
		identityService = processEngine.getIdentityService();
		processEngine.close();
	}

	public static RepositoryService getRepositoryService() {
		return repositoryService;
	}

	/**
	 * 发布流程
	 * 
	 * @param is
	 * @return
	 */
	public static String deploy(InputStream is) {
		ZipInputStream zis = new ZipInputStream(is);
		return repositoryService.createDeployment().addResourcesFromZipInputStream(zis).deploy();
	}

	/**
	 * 发布流程
	 * 
	 * @param id
	 * @return
	 */
	public static void deleteDeploymentCascade(String id) {
		repositoryService.deleteDeploymentCascade(id);
	}

	/**
	 * 发布流程(根据网络zip文件超链接路径)
	 * 
	 * @param url
	 * @return
	 * @throws MalformedURLException
	 */
	public static String deployUrl(String url) throws MalformedURLException {
		return deploy(new UrlStreamInput(new URL(url)).openStream());
	}

	// /**
	// * 通过模块名称查询流程PDID
	// *
	// * @param modelName
	// * @return
	// */
	// public static List<Object> getDeploymentListByModelName(String modelName)
	// {
	// return Dao.selectList("bpm.deployment.getDeploymentListByModelName",
	// modelName);
	// }

	/**
	 * 根据流程模块名称获取流程实例(作废)
	 * 
	 * @param MODULE
	 *            流程模块名称(中英文都可以)
	 * @return List 流程实例 KEY=PDID
	 * @author: 吴剑东
	 * @date: 2013-9-18 下午05:40:56
	 */
	@Deprecated
	public static List<String> getDeploymentListByModelName(String MODULE) {
		return getDeploymentListByModelName(MODULE, null, null);
	}
	/**
	 * 根据流程模块名称和事业部字段的值获取相关流程
	 * @author: 裴孟丽
	 * @date: 2013-9-18 下午05:40:56
	 */
	@Deprecated
	public static List<String> getDeploymentListByJos(Map<String,Object> param ) {
		System.out.println("param:"+param);
		return getDeploymentListByJoss(param);
	}
	
	/**
	 * @param module
	 *            模块编号
	 * @param projectType
	 *            业务类型编号
	 * @param platform
	 *            公司平台编号
	 * @return
	 */
	public static List<String> getDeploymentListByModelName(String module, String projectType, String platform) {
		Map<String, Object> m = new HashMap<>();
		m.put("MODULE", module);
		m.put("PROJECT_TYPE", projectType);
		m.put("PLATFORM", platform);
		return Dao.selectList("bpm.deployment.getPdidByModule", m);
	}
	
	public static List<String> getDeploymentListByJoss(Map<String,Object> map) {
		Map<String, Object> m = new HashMap<>();
		m.put("MODULE", map.get("MODULE"));
        m.put("PDKEY",map.get("PDKEY")+"事业部归档流程");
    	m.put("PROJECT_TYPE", null);
		m.put("PLATFORM", null);
		return Dao.selectList("bpm.deployment.getPdidByModule", m);
	}

	/**
	 * 启动流程实例
	 * @param id
	 * @param map
	 * @return
	 * @author <a href='mailto:lichaohn@163.com'>李超</a>
	 * @date 2011-8-9
	 */
	public static ProcessInstance startProcessInstanceById(String id, String projectId, String clientId, String payCode, Map<String, Object> map) {
		Map<String, String> param = new HashMap<>();
		param.put("OP_CODE", Security.getUser().getCode());
		param.put("OP_NAME", Security.getUser().getName());
		param.put("PROJECT_ID", projectId);
		param.put("CUST_ID", clientId);
		param.put("PAY_CODE", payCode);
		param.put("SUP_NAME", StringUtils.nullToString(map.get("SUP_NAME")));
		map.put("CREATEUSERCODE", Security.getUser().getCode());
		map.put("PROVINCE_NAME", map.get("PROVINCE_NAME"));
		ProcessInstance instance = executionService.startProcessInstanceById(id, map);
		param.put("JBPM_ID", instance.getId());
		// 将当前操作人和项目id加入流程实例
		Dao.update("bpm.procinst.upProcinst", param);

		// ------------------新增备注
		param.put("TASK_NAME", "开始");
		param.put("OP_TYPE", "发起流程");
		param.put("MEMO", "发起评审");
		Dao.insert("bpm.memo.add", param);
		com.pqsoft.bpm.service.TaskService taskService = new com.pqsoft.bpm.service.TaskService();
		List<Map<String, Object>> list = taskService.getTaskByJbpmId(instance.getId());
		for (int i = 0; i < list.size(); i++) {
			Map<String, Object> ms = list.get(i);
			try {
				Map<String, Object> claim = new HashMap<>();
				// TYPE:流程1数据2; TASK_NAME:任务名称;DATA_ID:数据id;DATA_NAME:描述;
				claim.put("TYPE", "1");
				claim.put("TASK_NAME", ms.get("ACTIVITY_NAME_"));
				claim.put("DATA_ID", ms.get("DBID_"));
				claim.put("USER_CODE", ms.get("CODE"));
				claim.put("DATA_NAME", ms.get("EXECUTION_ID_"));
				claim.put("PROJECT_ID", projectId);
				claim.put("URL", "bpm/Task!toShow.action?taskId=" + ms.get("DBID_"));
				claim.put("CREATEMAN_NAME", param.get("OP_NAME"));
				claim.put("CREATEMAN_CODE", param.get("OP_CODE"));
				claim.put("TASK_CREATETIME", param.get("START"));
				new TaskClaimService().addClaim(claim);
			} catch (Exception e) {
				e.printStackTrace();
			}
			try {
//				System.out.println(ms);
//				System.out.println(ms != null);
//				System.out.println(ms.get("CODE") != null);
//				System.out.println(!ms.get("CODE").equals(""));
				if (ms != null && ms.get("CODE") != null && !ms.get("CODE").equals("")) {
					String TYPE = "";
					// 邮件通知
					ms.put("JBPM_ID", instance.getId());
					if ("yyyy".equals(ms.get("EMAIL"))) {
//						// service.sendMail(ms.get("CODE").toString(),
//						// jbpmId);
						TYPE = "EMAIL";
						new RunInterfaceTemplate().sendJbpmMessage("task节点", ms, TYPE);
					}
					// 微信通知
					if ("yyyy".equals(ms.get("WX"))) {
//						WeixinService.sendTextMsg(ms.get("CODE").toString(), "通知：收到新任务--" + instance.getId(), "app5");
//						System.out.println("微信：" + instance.getId());
						TYPE = "WX";
						new RunInterfaceTemplate().sendJbpmMessage("task节点", ms, TYPE);
					}
					if ("yyyy".equals(ms.get("SMS"))) {
//						// 通知短信
//						// ms.get("MOBILE");// 手机号
						TYPE = "SMS";
//						ms.put("JBPM_ID", instance.getId());
						new RunInterfaceTemplate().sendJbpmMessage("task节点", ms, TYPE);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return instance;
	}

	//
	// /**
	// * 启动流程实例(包含启动备注)
	// *
	// * @param id
	// * @param map
	// * @return
	// * @author <a href='mailto:lichaohn@163.com'>李超</a>
	// * @date 2011-8-9
	// */
	// public static ProcessInstance startProcessInstanceById(String id, String
	// projectId, String clientId, String payCode, Map<String, Object> map,
	// String memo) {
	// Map<String, String> param = new HashMap<String, String>();
	// param.put("OP_CODE", Security.getUser().getCode());
	// param.put("OP_NAME", Security.getUser().getName());
	// param.put("PROJECT_ID", projectId);
	// param.put("CUST_ID", clientId);
	// param.put("PAY_CODE", payCode);
	// map.put("CREATEUSERCODE", Security.getUser().getCode());
	// ProcessInstance instance = executionService.startProcessInstanceById(id,
	// map);
	// param.put("JBPM_ID", instance.getId());
	//
	// // ------------------新增备注
	// param.put("TASK_NAME", "开始");
	// param.put("OP_TYPE", "发起流程");
	// param.put("MEMO", memo);
	// Dao.insert("bpm.memo.add", param);
	// // // ----------------
	// // 将当前操作人和项目id加入流程实例
	// Dao.update("bpm.procinst.upProcinst", param);
	// return instance;
	// }

	/**
	 * 获取发起流程时传入的参数
	 * 
	 * @param jbpmId
	 *            流程ID
	 * @return 发起流程时传入的参数
	 */
	public static Map<String, Object> getVeriable(String jbpmId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("JBPM_ID", jbpmId);
		List<Map<String, Object>> list = Dao.selectList("bpm.procinst.getVeriableByJbpmId", param);
		Map<String, Object> re = new HashMap<String, Object>();
		for (Map<String, Object> map : list) {
			re.put((String) map.get("KEY_"), map.get("STRING_VALUE_"));
		}
		return re;
	}

	public static String getShortName(String jbpmId) {
		String[] strs = jbpmId.split("[.]");
		if (strs.length > 2) {
			jbpmId = strs[0] + "." + strs[1];
		}
		return jbpmId;
	}

	public static Map<String, Object> getProInst(String jbpmId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("jbpmId", getShortName(jbpmId));
		return Dao.selectOne("bpm.procinst.getProInst", param);
	}

	/**
	 * 执行任务
	 * 
	 * @param id
	 * @param map
	 * @param str
	 * @author <a href='mailto:lichaohn@163.com'>李超</a>
	 * @date 2011-8-11
	 */
	static public void completeTask(String id, String str) {
		taskService.completeTask(id, str);
	}

	/**
	 * 设置局部任务变量
	 * 
	 * @author:King 2013-10-10
	 */
	static public void setTaskVariable(String jbpmId, Map<String, Object> map) {
		taskService.setVariables(jbpmId, map);
	}

	static public void completeTask(String id) {
		taskService.completeTask(id);
	}

	/**
	 * 设置全局变量
	 * 
	 * @param jbpmId
	 * @param map
	 * @author:King 2013-10-10
	 */
	static public void setExecutionVariable(String jbpmId, Map<String, Object> map) {
		executionService.setVariables(jbpmId, map);
	}

	public static void deleteProcessInstanceCascade(String jbpmId) {
		executionService.deleteProcessInstanceCascade(jbpmId);
	}

	public static void endProcessInstance(String jbpmId, String memo) {
		executionService.endProcessInstance(jbpmId, memo);
	}

	public static String getFormByProId(ProcessInstance jbpmIn, String jbpmExId) {
		Map<String, Object> m = new HashMap<String, Object>();

		m.put("PDID", jbpmExId);
		m.put("TASK_NAME", jbpmIn.findActiveActivityNames().iterator().next().toString());
		return Dao.selectOne("bpm.procinst.getFormByProId", m).toString();
	}

	/**
	 * @Title: getDeploymentNameByProcessId
	 * @Description: TODO(获取流程定义名称)
	 * @param jbpmId
	 * @return
	 * @return String
	 * @throws
	 */
	public static String getDeploymentNameByProcessId(String jbpmId) {
		String id = executionService.findProcessInstanceById(jbpmId).getProcessDefinitionId();
		return id;
	}

	/**
	 * @param task_name
	 * @Title: getConfigDelOpByProId
	 * @Description: TODO(根据流程定义id获取流程删除action)
	 * @param deployId
	 * @return
	 * @return String
	 * @throws
	 */
	public static String getConfigDelOpByProId(String deployId, String task_name) {
		if (deployId == null || task_name == null) return null;
		Map<String, Object> m = new HashMap<String, Object>();
		m.put("PDID", deployId);
		m.put("TASK_NAME", task_name);
		List l = Dao.selectList("bpm.procinst.getConfigDelOpByProId", m);
		String del_op = "";
		if (l.size() > 0 && StringUtils.isNotNull(l.get(0))) {
			del_op = StringUtils.nullToString(((Map) l.get(0)).get("DELETE_OP"));
		}
		return del_op;
	}

	public static String getTaskNameByTaskId(String TaskId) {
		Task task = taskService.getTask(TaskId);
		if (task == null) return null;
		String taskName = task.getName();
		return taskName;
	}
	// public static void getManagerInfo(String requestInitiator, JdbcBaseDao
	// conn, Map<String, String> workFlowDealersMap) throws Exception {
	// StringBuffer sb_queryManagerSql = new StringBuffer("");
	// // 查询发起人的部门经理
	// sb_queryManagerSql.append(" \n   select jb_yhxx.login_name as 登录名,jb_yhxx.real_name as 真实姓名 from( ")
	// .append(" \n      select jb_gsbm.bmjl from( ")
	// .append(" \n          select bm,login_name from jb_yhxx  where upper(login_name) =upper('"
	// + requestInitiator + "') ")
	// .append(" \n       )jb_yhxx inner join( ").append(" \n          select t.id,t.bmjl from jb_gsbm t ")
	// .append(" \n       )jb_gsbm on jb_yhxx.bm = jb_gsbm.id   ").append(" \n   )tt inner join( ")
	// .append(" \n       select id,login_name,jb_yhxx.xm as real_name from jb_yhxx ").append(" \n   )jb_yhxx on  jb_yhxx.id = tt.bmjl ");
	// List l = conn.doQueryBySql(sb_queryManagerSql.toString());
	// if (l.size() > 0) {
	// Map m = (Map) l.get(0);
	// String manageruser = StringUtil.nullToString(m.get("登录名"));
	// String managerrealname = StringUtil.nullToString(m.get("真实姓名"));
	// workFlowDealersMap.put("login_name", manageruser);
	// workFlowDealersMap.put("real_name", managerrealname);
	// // 获得代理用户
	// getDelegateInfo(manageruser, conn, workFlowDealersMap);
	// }
	// }

	// public static void getDelegateInfo(String nextDealUser, JdbcBaseDao conn,
	// Map<String, String> workFlowDealersMap) throws Exception {
	// String currentDate = StringUtil.getSystemDate();
	// String queryDelegateSql =
	// "select t.delegateuser as 代理登陆名,t.delegaterealname 代理真实身份 from t_jbpm_delegate t where grantuser ='"
	// + nextDealUser
	// + "' and t.start_date <='" + currentDate + "' and t.end_date>='" +
	// currentDate + "'";
	// List l = conn.doQueryBySql(queryDelegateSql);
	// if (l.size() > 0) {
	// Map m = (Map) l.get(0);
	// String delegateuser = StringUtil.nullToString(m.get("代理登陆名"));
	// String delegaterealname = StringUtil.nullToString(m.get("代理真实身份"));
	// workFlowDealersMap.put("delegate_login_name", delegateuser);
	// workFlowDealersMap.put("delegate_real_name", delegaterealname);
	// }
	// }
	//
	// public static void getRequestInitiatorDealer(String requestInitiator,
	// String requestInitiatorName, JdbcBaseDao conn,
	// List<Map<String, String>> workFlowDealersMapList) throws Exception {
	// Map<String, String> workFlowDealersMap = new HashMap<String, String>();
	// workFlowDealersMap.put("login_name", requestInitiator);
	// workFlowDealersMap.put("real_name", requestInitiatorName);
	// getDelegateInfo(requestInitiator, conn, workFlowDealersMap);
	// workFlowDealersMapList.add(workFlowDealersMap);
	// }
	//
	// public static String getUserRealName(String login_name, JdbcBaseDao conn)
	// throws Exception {
	// String sql = "select xm from jb_yhxx where login_name='" + login_name +
	// "'";
	// Map map = (Map) conn.doQueryBySql(sql).get(0);
	// String real_name =
	// StringUtil.nullToString(map.get(map.keySet().iterator().next()));
	// return real_name;
	// }
	//
	// public static void getVariableDealer(String variable, Map variablesMap,
	// JdbcBaseDao conn, List<Map<String, String>> workFlowDealersMapList)
	// throws Exception {
	// Map<String, String> workFlowDealersMap = new HashMap<String, String>();
	// String login_name = QueryUtil.getQueryStringByRequest(variablesMap,
	// variable);
	// workFlowDealersMap.put("login_name", login_name);
	// String real_name = JBPM.getUserRealName(login_name, conn);
	// workFlowDealersMap.put("real_name", real_name);
	// JBPM.getDelegateInfo(login_name, conn, workFlowDealersMap);
	// workFlowDealersMapList.add(workFlowDealersMap);
	// }
	//
	// public static void getSQLDealer(String sql, Map variablesMap, JdbcBaseDao
	// conn, List<Map<String, String>> workFlowDealersMapList)
	// throws Exception {
	// sql = QueryUtil.getQueryStringByRequest(variablesMap, sql);
	// List l = conn.doQueryBySql(sql);
	// for (int index = 0; index < l.size(); index++) {
	// Map<String, String> workFlowDealersMap = new HashMap<String, String>();
	// Map map = (Map) l.get(index);
	// String login_name =
	// StringUtil.nullToString(map.get(map.keySet().iterator().next()));
	// workFlowDealersMap.put("login_name", login_name);
	// String real_name = JBPM.getUserRealName(login_name, conn);
	// workFlowDealersMap.put("real_name", real_name);
	// JBPM.getDelegateInfo(login_name, conn, workFlowDealersMap);
	// workFlowDealersMapList.add(workFlowDealersMap);
	// }
	// }
	//
	// public static void getManagerDealer(String requestInitiator, JdbcBaseDao
	// conn, List<Map<String, String>> workFlowDealersMapList) throws Exception
	// {
	// Map<String, String> workFlowDealersMap = new HashMap<String, String>();
	// JBPM.getManagerInfo(requestInitiator, conn, workFlowDealersMap);
	// workFlowDealersMapList.add(workFlowDealersMap);
	// }
	//
	// public static void getAllDealersByGroup(List<Map<String, String>>
	// workFlowDealersMapList, String groupName, JdbcBaseDao conn) throws
	// Exception {
	// String sql =
	// "select jb_yhxx.login_name as 登录名,jb_yhxx.xm as 真实身份  from(select * from t_role where label like '"
	// + groupName
	// +
	// "')t_role inner join(select * from t_user_role)t_user_role on t_user_role.role_id = t_role.id inner join(select * from jb_yhxx)jb_yhxx on  jb_yhxx.id = t_user_role.user_id ";
	// List l = conn.doQueryBySql(sql);
	// int len = l.size();
	// if (len > 0) {
	// for (int i = 0; i < len; i++) {
	// Map<String, String> workFlowDealersMap = new HashMap<String, String>();
	// Map m = (Map) l.get(i);
	// String login_name = StringUtil.nullToString(m.get("登录名"));
	// String real_name = StringUtil.nullToString(m.get("真实身份"));
	// workFlowDealersMap.put("login_name", login_name);
	// workFlowDealersMap.put("real_name", real_name);
	// JBPM.getDelegateInfo(login_name, conn, workFlowDealersMap);
	// workFlowDealersMapList.add(workFlowDealersMap);
	// }
	// }
	// }
	//
	// // 获取blob中的xml文件内容
	// public static String queryBlob(JdbcBaseDao jbpmDao, final String sql)
	// throws Exception {
	// return jbpmDao.getJdbcTemplate().execute(sql, new
	// PreparedStatementCallback<String>() {
	// @Override
	// public String doInPreparedStatement(PreparedStatement ps) throws
	// SQLException, DataAccessException {
	// ResultSet rs = null;
	// StringBuffer sb = new StringBuffer("");
	// rs = ps.executeQuery(sql);
	// Reader read = null;
	// InputStream is = null;
	// try {
	// if (rs.next()) {
	// java.sql.Blob blob = rs.getBlob(1);
	// is = blob.getBinaryStream();
	// read = new InputStreamReader(is, "UTF-8");
	// char[] charbuf = new char[4096];
	// for (int i = read.read(charbuf); i > 0; i = read.read(charbuf)) {
	// sb.append(charbuf, 0, i);
	// }
	// }
	// read.close();
	// is.close();
	// } catch (UnsupportedEncodingException e) {
	// e.printStackTrace();
	// throw new SQLException("UnsupportedEncodingException");
	// } catch (IOException e) {
	// e.printStackTrace();
	// throw new SQLException("IOException");
	// }
	// return sb.toString();
	// }
	// });
	// }
	//
	// public static Map<String, Map<String, String>>
	// getAllWorkFlowNodesTransitions(String deployId, JdbcBaseDao jbpmDao,
	// String containerName)
	// throws Exception {
	// Map<String, Map<String, String>> allWorkFlowNodesTransitions = new
	// HashMap<String, Map<String, String>>();
	// String sql =
	// "select blob_value_ as 文件内容 from jbpm4_lob where name_ like '%.xml' and deployment_="
	// + deployId;
	// String xmlContent = JBPM.queryBlob(jbpmDao, sql);
	// Document document = XmlUtil.readXML(xmlContent, true);
	// Element root = document.getRootElement();
	// Namespace ns = root.getNamespace();
	// List taskElements = root.getChildren("task", ns);
	//
	// for (int i = 0; i < taskElements.size(); i++) {
	// Element taskElement = (Element) taskElements.get(i);
	// String nodeName =
	// StringUtil.nullToString(taskElement.getAttributeValue("name"));
	// Map<String, String> m = new HashMap<String, String>();
	// List transitionElements = taskElement.getChildren("transition", ns);
	// for (int j = 0; j < transitionElements.size(); j++) {
	// Element transitionElement = (Element) transitionElements.get(j);
	// String transitionTo =
	// StringUtil.nullToString(transitionElement.getAttributeValue("to"));
	// String transitionName =
	// StringUtil.nullToString(transitionElement.getAttributeValue("name"));
	// m.put(transitionTo, transitionName);
	// }
	// allWorkFlowNodesTransitions.put(nodeName, m);
	// }
	// document = null;
	// XmlUtil.closeLocalResources();
	// return allWorkFlowNodesTransitions;
	// }

}
