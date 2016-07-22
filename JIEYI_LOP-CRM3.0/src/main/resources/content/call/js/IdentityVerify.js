$(function(){
	$("#table1").datagrid({
		url : 'LeaseVerify!getPageIdentity.action',
		columns : [[
			{field:'ID',checkbox:true,width:100},
			{field:'CUST_NAME',title:'客户名称',width:100},
			{field:'PROJ_ID',title:'项目编号',width:100},
			{field:'PHONE',title:'联系方式',width:100},
			{field:'ISTRUE1',title:'是否正确',width:100},
			{field:'ISTRUE2',title:'是否本人接听',width:100},
			{field:'CARD_NO',title:'身份证号',width:100},
			{field:'ISTRUE3',title:'是否正确',width:100},
			{field:'RESIDENCE',title:'户籍所在地',width:100},
			{field:'ISTRUE4',title:'是否正确',width:100},
			{field:'ISMARRI',title:'婚姻状况',width:100},
			{field:'SPOUSE_NAME',title:'配偶姓名',width:100},
			{field:'ISTRUE5',title:'是否正确',width:100},
			{field:'REMARK',title:'备注',width:100},
			{field:'ISAGREE',title:'是否通过',width:100}
		]],
		toolbar : '#toolbar',//工具条
		pagination : true ,//分页
		idField : 'ID',
		fitColumns : true,
		autoRowHeight : true,
		//singleSelect : true,
		checkOnSelect : true,
		nowrap : true,
		rownumbers : true,
		
		pageSize : 20,
		pageList : [10,20,50,100,200,300]
	});
	
	$('#form01').form({
        url:""
    });
   
});
//查询
function se(){
	var searchParams = getFromData('#toolbar');
	$('#table1').datagrid('load',{"searchParams":searchParams});
}

//是否全选
function change(obj){
	$(obj).is(":checked") ? $('#table1').datagrid('selectAll') : $('#table1').datagrid('unselectAll') ;
}

//导出
function exportExcel(flag){
	
	//data
	var datagridList=$('#table1').datagrid('getChecked');
	var sqlData = [];	
	
	for(var i = 0; i < datagridList.length; i++)
	{
		sqlData.push("'"+datagridList[i].ID+"'");
	}
	
	//params
	var searchParams = getFromData('#toolbar');
	
	//url
	var url = "LeaseVerify!exportExcelIdentity.action";
	if(flag == 'all')
	{
		url += "?exportAll=true";
	}else
	{
		url += "?exportAll=false";
		if(datagridList.length == 0){
			alert("请选择要导出的数据");
			return;
		}
	}
	//submit
	$('#form01').form('submit',{
        url:url,
        onSubmit: function(){
			//查询参数
			if($('#searchParams').length<=0){
				$('#form01').append('<input name=\"searchParams\" id=\"searchParams\" type=\"hidden\" />');
			}
			$('#searchParams').val(searchParams);
			//导出标识
			if($('#sqlData').length<=0){
				$('#form01').append('<input name=\"sqlData\"  id=\"sqlData\" type=\"hidden\" />');
			}
			$('#sqlData').val(sqlData.join(','));
        }
    });
	//remove
	$('#sqlData').remove();
	$('#searchParams').remove();
}
//清空
function clearQuery(form01){
	$('#form01').form('clear');
	//$('#toolbar input').not(':checkbox').val('');
    //$('#toolbar select').val('');
}
