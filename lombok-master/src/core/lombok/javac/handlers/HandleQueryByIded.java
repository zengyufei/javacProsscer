package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import lombok.QueryByIded;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import org.mangosdk.spi.ProviderFor;

import static lombok.javac.Javac.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * Handles the {@code QueryByIded} annotation for javac.
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleQueryByIded extends JavacAnnotationHandler<QueryByIded> {

	private static final String Before_Name = "queryByIdedBefore";
	private static final String After_Name = "queryByIdedAfter";
	private static final String Module_Discript = "查询单个，包括删除";
	private static final String Method_Arg_Name = "id";
	private static final String Module_Key = "queryByIded";

	@Override
	public void handle(AnnotationValues<QueryByIded> annotation, JCAnnotation ast, JavacNode annotationNode) {
		// annotationNode 是上下文
		// 判断是否已经使用了注解
		// handleFlagUsage(annotationNode, ConfigurationKeys.TO_STRING_FLAG_USAGE, "@QueryByIded");

		// 如果存在则删除
		deleteAnnotationIfNeccessary(annotationNode, QueryByIded.class);

		// java.util.List<Object> extensionProviders = annotation.getActualExpressions("value");
		// String beanClassName = ((JCAssign) ast.args.last()).rhs.type.getTypeArguments().last().toString();
		String className = ((JCAssign) ast.args.last()).rhs.toString();
		String beanClassName = className.substring(0, className.indexOf("."));
		// 获取该注解的地方，如果注解在类上面，则获取该类，如果注解在方法上，则获取该方法，如此类推
		JavacNode typeNode = annotationNode.up();

		// 获取该注解，并接下来获取该注解上的属性
		QueryByIded ann = annotation.getInstance();
		java.util.List<Object> extensionProviders = annotation.getActualExpressions("value");
		for (Object extensionProvider : extensionProviders) {
			if (!(extensionProvider instanceof JCFieldAccess)) continue;
			JCFieldAccess provider = (JCFieldAccess) extensionProvider;
			beanClassName = ((JCIdent) provider.selected).sym.toString();
		}

		generateQueryByIded(typeNode, annotationNode, beanClassName, true);
	}

	public void generateQueryByIded(JavacNode typeNode, JavacNode source, String beanClass, boolean whineIfExists) {
		boolean notAClass = true;
		if (typeNode.get() instanceof JCClassDecl) {
			long flags = ((JCClassDecl) typeNode.get()).mods.flags;
			notAClass = (flags & (Flags.INTERFACE | Flags.ANNOTATION)) != 0;
		}

		if (notAClass) {
			source.addError("@QueryByIded is only supported on a class or enum.");
			return;
		}

		boolean isExistsBefore;
		switch (methodExists(Before_Name, typeNode, 1)) {
			case NOT_EXISTS:
				JCMethodDecl method = createQueryByIdedBefore(typeNode, source.get());
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
					source.addWarning("Not generating queryByIded(): A method with that name already exists");
				}
				break;
		}

		boolean isExistsAfter;
		switch (methodExists(After_Name, typeNode, 2)) {
			case NOT_EXISTS:
				JCMethodDecl method = createQueryByIdedAfter(typeNode, beanClass, source.get());
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
					source.addWarning("Not generating queryByIded(): A method with that name already exists");
				}
				break;
		}

		switch (methodExists(Module_Key, typeNode, 0)) {
			case NOT_EXISTS:
				JCMethodDecl method = createQueryByIded(typeNode, beanClass, source.get(), isExistsBefore, isExistsAfter);
				injectMethod(typeNode, method);
				break;
			case EXISTS_BY_LOMBOK:
				break;
			default:
			case EXISTS_BY_USER:
				if (whineIfExists) {
					source.addWarning("Not generating queryByIded(): A method with that name already exists");
				}
				break;
		}


	}

	static JCMethodDecl createQueryByIdedBefore(JavacNode typeNode, JCTree source) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		Name longIdName = typeNode.toName(Method_Arg_Name);

		JCExpression keyType = chainDotsString(typeNode, "java.lang.Long");

		// 添加一个 注解 @org.springframework.validation.annotation.Validated({com.zyf.valid.QueryByIded.class})
		JCExpression validNoNullArg11 = maker.Assign(maker.Ident(typeNode.toName("message")), maker.Literal("Id 不能为空"));
		JCExpression validNoNullArg12 = maker.Assign(maker.Ident(typeNode.toName("value")), maker.Literal(CTC_INT, 1));
		JCExpression validNoNullArg2 = maker.Assign(maker.Ident(typeNode.toName("message")), maker.Literal("Id 不能为空"));
		JCAnnotation validAnnotation1 = maker
				.Annotation(genTypeRef(typeNode, "javax.validation.constraints.Min"),
						List.of(validNoNullArg11, validNoNullArg12));

		JCAnnotation validAnnotation2 = maker
				.Annotation(genTypeRef(typeNode, "javax.validation.constraints.NotNull"),
						List.of(validNoNullArg2));
		// Long id
		JCVariableDecl LongId = maker.VarDef(maker.Modifiers(Flags.PARAMETER,
				List.<JCAnnotation>of(validAnnotation1, validAnnotation2)), longIdName, keyType, null);

		Name queryByIdedBeforeName = typeNode.toName(Before_Name);
		// protected
		JCModifiers modifiers = maker.Modifiers(Flags.PROTECTED);
		// void
		JCExpression beforeType = maker.Type(createVoidType(typeNode.getSymbolTable(), CTC_VOID));
		// {}
		JCBlock block = maker.Block(0, List.<JCStatement>nil());
		// protected void queryByIdedBefore(Long id) {}
		JCMethodDecl queryByIdedBeforeMethod = maker.MethodDef(modifiers, queryByIdedBeforeName, beforeType,
				List.<JCTypeParameter>nil(), List.of(LongId), List.<JCExpression>nil(), block, null);
		return recursiveSetGeneratedBy(queryByIdedBeforeMethod, source, typeNode.getContext());
	}

	static JCMethodDecl createQueryByIdedAfter(JavacNode typeNode, String beanClassName, JCTree source) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		Name longIdName = typeNode.toName(Method_Arg_Name);
		Name objName = typeNode.toName("obj");

		JCExpression keyType = chainDotsString(typeNode, "java.lang.Long");
		JCExpression beanType = chainDotsString(typeNode, beanClassName);

		// 添加一个 注解 @org.springframework.validation.annotation.Validated({com.zyf.valid.QueryByIded.class})
		JCExpression validNoNullArg11 = maker.Assign(maker.Ident(typeNode.toName("message")), maker.Literal("Id 不能为空"));
		JCExpression validNoNullArg12 = maker.Assign(maker.Ident(typeNode.toName("value")), maker.Literal(CTC_INT, 1));
		JCExpression validNoNullArg2 = maker.Assign(maker.Ident(typeNode.toName("message")), maker.Literal("Id 不能为空"));
		JCAnnotation validAnnotation1 = maker
				.Annotation(genTypeRef(typeNode, "javax.validation.constraints.Min"),
						List.of(validNoNullArg11, validNoNullArg12));

		JCAnnotation validAnnotation2 = maker
				.Annotation(genTypeRef(typeNode, "javax.validation.constraints.NotNull"),
						List.of(validNoNullArg2));
		// Long id
		JCVariableDecl LongId = maker.VarDef(maker.Modifiers(Flags.PARAMETER,
				List.<JCAnnotation>of(validAnnotation1, validAnnotation2)), longIdName, keyType, null);
		// C obj
		JCVariableDecl param2 = maker.VarDef(maker.Modifiers(Flags.PARAMETER), objName, beanType, null);

		Name queryByIdedAfterName = typeNode.toName(After_Name);
		// protected
		JCModifiers modifiers = maker.Modifiers(Flags.PROTECTED);
		// void
		JCExpression afterType = maker.Type(createVoidType(typeNode.getSymbolTable(), CTC_VOID));
		// {}
		JCBlock block = maker.Block(0, List.<JCStatement>nil());
		// protected void queryByIdedAfter(Long id, C LongId) {}
		JCMethodDecl queryByIdedAfterMethod = maker.MethodDef(modifiers, queryByIdedAfterName, afterType,
				List.<JCTypeParameter>nil(), List.of(LongId, param2), List.<JCExpression>nil(), block, null);

		return recursiveSetGeneratedBy(queryByIdedAfterMethod, source, typeNode.getContext());
	}

	static JCMethodDecl createQueryByIded(JavacNode typeNode, String beanClassName, JCTree source, boolean isExistsBefore, boolean isExistsAfter) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		// 添加一个 注解
		JCExpression annotationArg = maker.Literal(Module_Key); // 序列成一个字符串
		JCAnnotation overrideAnnotation = maker.Annotation(genTypeRef(typeNode, "org.springframework.web.bind.annotation.GetMapping"), List.of(annotationArg));
		// 添加第二个 注解
		JCExpression annotationArg21 = maker.Assign(maker.Ident(typeNode.toName("value")), maker.Literal("序号"));
		JCExpression annotationArg22 = maker.Assign(maker.Ident(typeNode.toName("paramType")), maker.Literal("query"));
		JCExpression annotationArg23 = maker.Assign(maker.Ident(typeNode.toName("name")), maker.Literal(Method_Arg_Name));
		JCExpression annotationArg24 = maker.Assign(maker.Ident(typeNode.toName("required")), maker.Literal(CTC_BOOLEAN, 1));
		JCAnnotation overrideAnnotation2 = maker.Annotation(genTypeRef(typeNode, "io.swagger.annotations.ApiImplicitParam"),
				List.of(annotationArg21, annotationArg22, annotationArg23, annotationArg24));
		// 添加第三个 注解
		JCExpression annotationArg31 = maker.Assign(maker.Ident(typeNode.toName("value")), maker.Literal(Module_Discript));
		JCExpression annotationArg32 = maker.Assign(maker.Ident(typeNode.toName("notes")), maker.Literal(Module_Discript));
		JCExpression annotationArg33 = maker.Assign(maker.Ident(typeNode.toName("httpMethod")), maker.Literal("GET"));
		JCAnnotation overrideAnnotation3 = maker.Annotation(genTypeRef(typeNode, "io.swagger.annotations.ApiOperation"),
				List.of(annotationArg31, annotationArg32, annotationArg33));
		// 添加第四个 注解
		JCExpression annotationArg41 = maker.Assign(maker.Ident(typeNode.toName("code")), genTypeRef(typeNode, "com.zyf.result.Msg.SUCCESS_CODE"));
		JCExpression annotationArg42 = maker.Assign(maker.Ident(typeNode.toName("message")), maker.Literal(Module_Discript + "成功"));
		JCExpression annotationArg43 = maker.Assign(maker.Ident(typeNode.toName("response")), genTypeRef(typeNode, "com.zyf.result.Msg.class"));
		JCAnnotation overrideAnnotation4 = maker.Annotation(genTypeRef(typeNode, "io.swagger.annotations.ApiResponse"),
				List.of(annotationArg41, annotationArg42, annotationArg43));

		// 附加注解到方法上
		JCModifiers mods = maker.Modifiers(Flags.PUBLIC, List.of(overrideAnnotation, overrideAnnotation2, overrideAnnotation3, overrideAnnotation4));

		Name longIdName = typeNode.toName(Method_Arg_Name);
		Name objName = typeNode.toName("obj");

		JCExpression keyType = chainDotsString(typeNode, "java.lang.Long");
		JCExpression beanType = chainDotsString(typeNode, beanClassName);

		// 添加一个 注解 @org.springframework.validation.annotation.Validated({com.zyf.valid.QueryByIded.class})
		JCExpression validNoNullArg11 = maker.Assign(maker.Ident(typeNode.toName("message")), maker.Literal("Id 不能为空"));
		JCExpression validNoNullArg12 = maker.Assign(maker.Ident(typeNode.toName("value")), maker.Literal(CTC_INT, 1));
		JCExpression validNoNullArg2 = maker.Assign(maker.Ident(typeNode.toName("message")), maker.Literal("Id 不能为空"));
		JCAnnotation validAnnotation1 = maker
				.Annotation(genTypeRef(typeNode, "javax.validation.constraints.Min"),
						List.of(validNoNullArg11, validNoNullArg12));

		JCAnnotation validAnnotation2 = maker
				.Annotation(genTypeRef(typeNode, "javax.validation.constraints.NotNull"),
						List.of(validNoNullArg2));
		// Long id
		JCVariableDecl LongId = maker.VarDef(maker.Modifiers(Flags.PARAMETER,
				List.<JCAnnotation>of(validAnnotation1, validAnnotation2)), longIdName, keyType, null);
		// C obj
		JCVariableDecl param2 = maker.VarDef(maker.Modifiers(Flags.PARAMETER), objName, beanType, null);

		JCBlock body;
		// name = queryByIdedBefore

		// LongId.queryByIded
		JCExpression objMethod = chainDots(typeNode, "io", "ebean", "Ebean", "find");
		// bean class
		JCExpression beanClass = genTypeRef(typeNode, beanClassName + ".class");
		// id
		JCExpression objArgs = maker.Ident(longIdName);
		// Ebean.find(SysOrg.class)
		JCExpression objMethodSuccess = maker.Apply(List.<JCExpression>nil(), objMethod, List.<JCExpression>of(beanClass));
		// Ebean.find(SysOrg.class).setIncludeSoftDeletes
		JCFieldAccess setIncludeSoftDeletes = maker.Select(objMethodSuccess, typeNode.toName("setIncludeSoftDeletes"));
		// Ebean.find(SysOrg.class).setIncludeSoftDeletes()
		JCExpression setIncludeSoftDeletesEnd = maker.Apply(List.<JCExpression>nil(), setIncludeSoftDeletes,
				List.<JCExpression>nil());
		// Ebean.find(SysOrg.class).setIncludeSoftDeletes.setId
		JCFieldAccess setId = maker.Select(setIncludeSoftDeletesEnd, typeNode.toName("setId"));
		// Ebean.find(SysOrg.class).setIncludeSoftDeletes().setId(1)
		JCExpression setIdEnd = maker.Apply(List.<JCExpression>nil(), setId,
				List.<JCExpression>of(objArgs));
		// Ebean.find(SysOrg.class).setIncludeSoftDeletes.setId(1).findUnique
		JCFieldAccess findUnique = maker.Select(setIdEnd, typeNode.toName("findUnique"));
		// Ebean.find(SysOrg.class).setIncludeSoftDeletes().setId(1).findUnique()
		JCExpression findUniqueEnd = maker.Apply(List.<JCExpression>nil(), findUnique,
				List.<JCExpression>nil());
		// C obj = Ebean.find(SysOrg.class).setIncludeSoftDeletes().setId(1).findUnique();
		JCVariableDecl temp = maker.VarDef(maker.Modifiers(0), param2.getName(), beanType, findUniqueEnd);

		JCIdent tempEnd = maker.Ident(temp.getName());
		Name queryByIdedBeforeName = typeNode.toName(Before_Name);
		Name queryByIdedAfterName = typeNode.toName(After_Name);

		// 判断 queryByIdedBefore 方法是否存在
		JCStatement beforeStatement = null;
		if (isExistsBefore) {
			// queryByIdedAfter
			// queryByIdedBefore
			JCExpression beforeMethod = maker.Ident(queryByIdedBeforeName);
			// id
			JCExpression beforeArgs = maker.Ident(longIdName);
			// queryByIdedBefore(id)
			JCExpression beforeMethodSuccess = maker.Apply(List.<JCExpression>nil(), beforeMethod, List.of(beforeArgs));
			// queryByIdedBefore(id);
			beforeStatement = maker.Exec(beforeMethodSuccess);
		}

		// 判断 queryByIdedAfter 方法是否存在
		JCStatement afterStatement = null;
		if (isExistsAfter) {
			// queryByIdedAfter
			JCExpression afterMethod = maker.Ident(queryByIdedAfterName);
			// id
			JCExpression afterArgs = maker.Ident(longIdName);
			// queryByIdedAfter(id, obj)
			JCExpression afterMethodSuccess = maker.Apply(List.<JCExpression>nil(), afterMethod, List.of(afterArgs, tempEnd));
			// queryByIdedAfter(id, obj);
			afterStatement = maker.Exec(afterMethodSuccess);
		}


		// 设定返回值类型
		JCExpression returnType = genTypeRef(typeNode, "com.zyf.result.Msg");
		JCExpression tsMethod = chainDots(typeNode, "com", "zyf", "result", "Msg", "ok");
		JCExpression current = maker.Apply(List.<JCExpression>nil(), tsMethod, List.<JCExpression>of(tempEnd));
		JCStatement returnStatement = maker.Return(current);

		List<JCStatement> of;
		if(beforeStatement != null && afterStatement != null){
			of = List.of(beforeStatement, temp, afterStatement, returnStatement);
		}else if(beforeStatement == null && afterStatement != null){
			of = List.of(temp, afterStatement, returnStatement);
		}else if(beforeStatement != null && afterStatement == null){
			of = List.of(beforeStatement, temp, returnStatement);
		}else{
			of = List.of(temp, returnStatement);
		}
		body = maker.Block(0, of);

		// method
		JCMethodDecl queryByIded = maker.MethodDef(mods, typeNode.toName(Module_Key), returnType,
				List.<JCTypeParameter>nil(), List.of(LongId), List.<JCExpression>nil(), body, null);
		return recursiveSetGeneratedBy(queryByIded, source, typeNode.getContext());
	}
}
