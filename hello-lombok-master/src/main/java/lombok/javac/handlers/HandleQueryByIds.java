package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import lombok.QueryByIds;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import org.mangosdk.spi.ProviderFor;

import static lombok.javac.Javac.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * Handles the {@code QueryByIds} annotation for javac.
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleQueryByIds extends JavacAnnotationHandler<QueryByIds> {

	private static final String Before_Name = "queryByIdsBefore";
	private static final String After_Name = "queryByIdsAfter";
	private static final String Module_Discript = "id 集合，不包括删除";
	private static final String Method_Arg_Name = "ids";
	private static final String Module_Key = "queryByIds";

	@Override
	public void handle(AnnotationValues<QueryByIds> annotation, JCAnnotation ast, JavacNode annotationNode) {
		// annotationNode 是上下文
		// 判断是否已经使用了注解
		// handleFlagUsage(annotationNode, ConfigurationKeys.TO_STRING_FLAG_USAGE, "@QueryByIds");

		// 如果存在则删除
		deleteAnnotationIfNeccessary(annotationNode, QueryByIds.class);

		// java.util.List<Object> extensionProviders = annotation.getActualExpressions("value");
		// String beanClassName = ((JCAssign) ast.args.last()).rhs.type.getTypeArguments().last().toString();
		String className = ((JCAssign) ast.args.last()).rhs.toString();
		String beanClassName = className.substring(0, className.indexOf("."));
		// 获取该注解的地方，如果注解在类上面，则获取该类，如果注解在方法上，则获取该方法，如此类推
		JavacNode typeNode = annotationNode.up();

		// 获取该注解，并接下来获取该注解上的属性
		QueryByIds ann = annotation.getInstance();
		java.util.List<Object> extensionProviders = annotation.getActualExpressions("value");
		for (Object extensionProvider : extensionProviders) {
			if (!(extensionProvider instanceof JCFieldAccess)) continue;
			JCFieldAccess provider = (JCFieldAccess) extensionProvider;
			beanClassName = ((JCIdent) provider.selected).sym.toString();
		}

		generateQueryByIds(typeNode, annotationNode, beanClassName, true);
	}

	public void generateQueryByIds(JavacNode typeNode, JavacNode source, String beanClass, boolean whineIfExists) {
		boolean notAClass = true;
		if (typeNode.get() instanceof JCClassDecl) {
			long flags = ((JCClassDecl) typeNode.get()).mods.flags;
			notAClass = (flags & (Flags.INTERFACE | Flags.ANNOTATION)) != 0;
		}

		if (notAClass) {
			source.addError("@QueryByIds is only supported on a class or enum.");
			return;
		}

		boolean isExistsBefore;
		switch (methodExists(Before_Name, typeNode, 1)) {
			case NOT_EXISTS:
				JCMethodDecl method = createQueryByIdsBefore(typeNode, source.get());
				injectMethod(typeNode, method);
				isExistsBefore = true;
				break;
			case EXISTS_BY_LOMBOK:
				isExistsBefore = true;
				break;
			default:
			case EXISTS_BY_USER:
				isExistsBefore = true;
				if (whineIfExists) {
					source.addWarning("Not generating queryByIds(): A method with that name already exists");
				}
				break;
		}

		boolean isExistsAfter;
		switch (methodExists(After_Name, typeNode, 1)) {
			case NOT_EXISTS:
				JCMethodDecl method = createQueryByIdsAfter(typeNode, beanClass, source.get());
				injectMethod(typeNode, method);
				isExistsAfter = true;
				break;
			case EXISTS_BY_LOMBOK:
				isExistsAfter = true;
				break;
			default:
			case EXISTS_BY_USER:
				isExistsAfter = true;
				if (whineIfExists) {
					source.addWarning("Not generating queryByIds(): A method with that name already exists");
				}
				break;
		}

		switch (methodExists(Module_Key, typeNode, 0)) {
			case NOT_EXISTS:
				JCMethodDecl method = createQueryByIds(typeNode, beanClass, source.get(), isExistsBefore, isExistsAfter);
				injectMethod(typeNode, method);
				break;
			case EXISTS_BY_LOMBOK:
				break;
			default:
			case EXISTS_BY_USER:
				if (whineIfExists) {
					source.addWarning("Not generating queryByIds(): A method with that name already exists");
				}
				break;
		}


	}

	static JCMethodDecl createQueryByIdsBefore(JavacNode typeNode, JCTree source) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		Name longIdName = typeNode.toName(Method_Arg_Name);
		JCExpression keyType = chainDotsString(typeNode, "java.lang.String");

		// String ids
		JCVariableDecl LongId = maker.VarDef(maker.Modifiers(Flags.PARAMETER), longIdName, keyType, null);

		Name queryByIdsBeforeName = typeNode.toName(Before_Name);
		// protected
		JCModifiers modifiers = maker.Modifiers(Flags.PROTECTED);
		// void
		JCExpression beforeType = maker.Type(createVoidType(typeNode.getTreeMaker(), CTC_VOID));
		// {}
		JCBlock block = maker.Block(0, List.<JCStatement>nil());
		// protected void queryByIdsBefore(Long id) {}
		JCMethodDecl queryByIdsBeforeMethod = maker.MethodDef(modifiers, queryByIdsBeforeName, beforeType,
				List.<JCTypeParameter>nil(), List.of(LongId), List.<JCExpression>nil(), block, null);
		return recursiveSetGeneratedBy(queryByIdsBeforeMethod, source, typeNode.getContext());
	}

	static JCMethodDecl createQueryByIdsAfter(JavacNode typeNode, String beanClassName, JCTree source) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		Name longIdName = typeNode.toName(Method_Arg_Name);
		JCExpression keyType = chainDotsString(typeNode, "java.lang.String");

		// java.util.List
		JCExpression argLeft = chainDotsString(typeNode, "java.util.List");
		// java.util.List<SysOrg>
		JCExpression argLeftEnd = maker.TypeApply(argLeft, List.<JCExpression>of(chainDotsString(typeNode, beanClassName)));
		// java.util.List<SysOrg> list
		JCVariableDecl listLeft = maker.VarDef(maker.Modifiers(Flags.PARAMETER), typeNode.toName("list"), argLeftEnd, null);


		// String ids
		JCVariableDecl LongId = maker.VarDef(maker.Modifiers(Flags.PARAMETER), longIdName, keyType, null);

		Name queryByIdsAfterName = typeNode.toName(After_Name);
		// protected
		JCModifiers modifiers = maker.Modifiers(Flags.PROTECTED);
		// void
		JCExpression beforeType = maker.Type(createVoidType(typeNode.getTreeMaker(), CTC_VOID));
		// {}
		JCBlock block = maker.Block(0, List.<JCStatement>nil());
		// protected void queryByIdsAfter(Long id, List<SysOrg> list) {}
		JCMethodDecl queryByIdsAfterMethod = maker.MethodDef(modifiers, queryByIdsAfterName, beforeType,
				List.<JCTypeParameter>nil(), List.of(LongId, listLeft), List.<JCExpression>nil(), block, null);
		return recursiveSetGeneratedBy(queryByIdsAfterMethod, source, typeNode.getContext());
	}

	static JCMethodDecl createQueryByIds(JavacNode typeNode, String beanClassName, JCTree source, boolean isExistsBefore, boolean isExistsAfter) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		// 添加一个 注解
		JCExpression annotationArg = maker.Literal(Module_Key); // 序列成一个字符串
		JCAnnotation overrideAnnotation = maker.Annotation(genTypeRef(typeNode, "org.springframework.web.bind.annotation.GetMapping"), List.of(annotationArg));
		// 添加第二个 注解
		JCExpression annotationArg21 = maker.Assign(maker.Ident(typeNode.toName("value")), maker.Literal("id 集合"));
		JCExpression annotationArg22 = maker.Assign(maker.Ident(typeNode.toName("paramType")), maker.Literal("query"));
		JCExpression annotationArg23 = maker.Assign(maker.Ident(typeNode.toName("name")), maker.Literal(Method_Arg_Name));
		JCExpression annotationArg24 = maker.Assign(maker.Ident(typeNode.toName("required")), maker.Literal(CTC_BOOLEAN, 1));
		JCExpression annotationArg25 = maker.Assign(maker.Ident(typeNode.toName("dataType")), maker.Literal("string"));
		JCAnnotation overrideAnnotation2 = maker.Annotation(genTypeRef(typeNode, "io.swagger.annotations.ApiImplicitParam"),
				List.of(annotationArg21, annotationArg22, annotationArg23, annotationArg24, annotationArg25));
		// 添加第三个 注解
		JCExpression annotationArg31 = maker.Assign(maker.Ident(typeNode.toName("value")), maker.Literal(Module_Discript));
		JCExpression annotationArg32 = maker.Assign(maker.Ident(typeNode.toName("notes")), maker.Literal(Module_Discript));
		JCExpression annotationArg33 = maker.Assign(maker.Ident(typeNode.toName("httpMethod")), maker.Literal("GET"));
		JCAnnotation overrideAnnotation3 = maker.Annotation(genTypeRef(typeNode, "io.swagger.annotations.ApiOperation"),
				List.of(annotationArg31, annotationArg32, annotationArg33));
		// 添加第四个 注解
		JCExpression annotationArg41 = maker.Assign(maker.Ident(typeNode.toName("code")), genTypeRef(typeNode, "com.zyf.result.Msg.SUCCESS_CODE"));
		JCExpression annotationArg42 = maker.Assign(maker.Ident(typeNode.toName("message")), maker.Literal("查询 " + Module_Discript + "成功"));
		JCExpression annotationArg43 = maker.Assign(maker.Ident(typeNode.toName("response")), genTypeRef(typeNode, "com.zyf.result.Msg.class"));
		JCAnnotation overrideAnnotation4 = maker.Annotation(genTypeRef(typeNode, "io.swagger.annotations.ApiResponse"),
				List.of(annotationArg41, annotationArg42, annotationArg43));

		// 附加注解到方法上
		JCModifiers mods = maker.Modifiers(Flags.PUBLIC,
				List.of(overrideAnnotation, overrideAnnotation2, overrideAnnotation3, overrideAnnotation4));

		Name longIdName = typeNode.toName(Method_Arg_Name);

		JCExpression keyType = chainDotsString(typeNode, "java.lang.String");

		// 添加一个 注解 @org.springframework.validation.annotation.Validated({com.zyf.valid.QueryByIds.class})
		JCExpression validNoNullArg11 = maker.Assign(maker.Ident(typeNode.toName("message")), maker.Literal("ids 集合不能为空"));
		JCAnnotation validAnnotation1 = maker
				.Annotation(genTypeRef(typeNode, "org.hibernate.validator.constraints.NotBlank"),
						List.of(validNoNullArg11));
		// Long id
		JCVariableDecl LongId = maker.VarDef(maker.Modifiers(Flags.PARAMETER,
				List.<JCAnnotation>of(validAnnotation1)), longIdName, keyType, null);

		JCBlock body;
		// name = queryByIdsBefore

		// LongId.queryByIds
		JCExpression objMethod = chainDots(typeNode, "io", "ebean", "Ebean", "find");
		// bean class
		JCExpression beanClass = genTypeRef(typeNode, beanClassName + ".class");
		// id
		JCExpression objArgs = maker.Ident(longIdName);
		// Ebean.find(SysOrg.class)
		JCExpression objMethodSuccess = maker.Apply(List.<JCExpression>nil(), objMethod, List.<JCExpression>of(beanClass));

		// Ebean.find(SysOrg.class).where
		JCFieldAccess where = maker.Select(objMethodSuccess, typeNode.toName("where"));
		// Ebean.find(SysOrg.class).where()
		JCExpression whereEnd = maker.Apply(List.<JCExpression>nil(), where, List.<JCExpression>nil());

		// ids.split
		JCFieldAccess split = maker.Select(objArgs, typeNode.toName("split"));
		// ","
		JCLiteral literal = maker.Literal(",");
		// ids.split(",")
		JCExpression splitEnd = maker.Apply(List.<JCExpression>nil(), split, List.<JCExpression>of(literal));

		// Arrays.asList
		JCExpression asList = chainDotsString(typeNode, "java.util.Arrays.asList");
		// Arrays.asList(ids.split(","))
		JCExpression asListEnd = maker.Apply(List.<JCExpression>nil(), asList, List.<JCExpression>of(splitEnd));

		// Ebean.find(SysOrg.class).where().idIn
		JCFieldAccess idIn = maker.Select(whereEnd, typeNode.toName("idIn"));
		// Ebean.find(SysOrg.class).where().idIn(Arrays.asList(ids.split(",")))
		JCExpression idInEnd = maker.Apply(List.<JCExpression>nil(), idIn, List.<JCExpression>of(asListEnd));

		// Ebean.find(SysOrg.class).where().idIn(Arrays.asList(ids.split(","))).findList
		JCFieldAccess findList = maker.Select(idInEnd, typeNode.toName("findList"));
		// Ebean.find(SysOrg.class).where().idIn(Arrays.asList(ids.split(","))).findList()
		JCExpression findListEnd = maker.Apply(List.<JCExpression>nil(), findList, List.<JCExpression>nil());

		// java.util.List
		JCExpression argLeft = chainDotsString(typeNode, "java.util.List");
		// java.util.List<SysOrg>
		JCExpression argLeftEnd = maker.TypeApply(argLeft, List.<JCExpression>of(chainDotsString(typeNode, beanClassName)));
		// java.util.List<SysOrg> list = Ebean.find(entityClass).where().idIn(Arrays.asList(ids.split(","))).findList();
		JCVariableDecl listLeft = maker.VarDef(maker.Modifiers(0), typeNode.toName("list"), argLeftEnd, findListEnd);

		Name queryByIdsBeforeName = typeNode.toName(Before_Name);
		Name queryByIdsAfterName = typeNode.toName(After_Name);

		// 判断 queryByIdsBefore 方法是否存在
		JCStatement beforeStatement = null;
		if (isExistsBefore) {
			// queryByIdsAfter
			// queryByIdsBefore
			JCExpression beforeMethod = maker.Ident(queryByIdsBeforeName);
			// id
			JCExpression beforeArgs = maker.Ident(longIdName);
			// queryByIdsBefore(id)
			JCExpression beforeMethodSuccess = maker.Apply(List.<JCExpression>nil(), beforeMethod, List.of(beforeArgs));
			// queryByIdsBefore(id);
			beforeStatement = maker.Exec(beforeMethodSuccess);
		}

		// 判断 queryByIdsAfter 方法是否存在
		JCStatement afterStatement = null;
		if (isExistsAfter) {
			// queryByIdsAfter
			JCExpression afterMethod = maker.Ident(queryByIdsAfterName);
			// id
			JCExpression afterArgs = maker.Ident(longIdName);
			// list
			JCExpression afterArgs2 = maker.Ident(listLeft.getName());
			// queryByIdsAfter(id, list)
			JCExpression afterMethodSuccess = maker.Apply(List.<JCExpression>nil(), afterMethod, List.of(afterArgs, afterArgs2));
			// queryByIdsAfter(id, list);
			afterStatement = maker.Exec(afterMethodSuccess);
		}


		// com.zyf.result.Msg
		JCExpression returnType = genTypeRef(typeNode, "com.zyf.result.Msg");
		// com.zyf.result.Msg.ok
		JCExpression tsMethod = chainDots(typeNode, "com", "zyf", "result", "Msg", "ok");
		// com.zyf.result.Msg.ok(list)
		JCExpression current = maker.Apply(List.<JCExpression>nil(), tsMethod, List.<JCExpression>of(maker.Ident(listLeft.getName())));
		JCStatement returnStatement = maker.Return(current);

		List<JCStatement> of;
		if(beforeStatement != null && afterStatement != null){
			of = List.of(beforeStatement, listLeft, afterStatement, returnStatement);
		}else if(beforeStatement == null && afterStatement != null){
			of = List.of(listLeft, afterStatement, returnStatement);
		}else if(beforeStatement != null && afterStatement == null){
			of = List.of(beforeStatement, listLeft, returnStatement);
		}else{
			of = List.of(listLeft, returnStatement);
		}
		body = maker.Block(0, of);

		// method
		JCMethodDecl queryByIds = maker.MethodDef(mods, typeNode.toName(Module_Key), returnType,
				List.<JCTypeParameter>nil(), List.of(LongId), List.<JCExpression>nil(), body, null);
		return recursiveSetGeneratedBy(queryByIds, source, typeNode.getContext());
	}
}
