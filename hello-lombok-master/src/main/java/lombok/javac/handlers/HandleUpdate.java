package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import lombok.Update;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import org.mangosdk.spi.ProviderFor;

import java.util.Iterator;

import static lombok.javac.Javac.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * 自动添加 修改方法
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleUpdate extends JavacAnnotationHandler<Update> {

	private static final String Before_Name = "updateBefore";
	private static final String After_Name = "updateAfter";
	private static final String Module_Discript = "修改";
	private static final String Method_Arg_Name = "obj";
	private static final String Module_Key = "update";

	@Override
	public void handle(AnnotationValues<Update> annotation, JCAnnotation ast, JavacNode annotationNode) {
		// annotationNode 是上下文
		// 判断是否已经使用了注解
		// handleFlagUsage(annotationNode, ConfigurationKeys.TO_STRING_FLAG_USAGE, "@Update");

		// 如果存在则删除
		deleteAnnotationIfNeccessary(annotationNode, Update.class);

		// 获取该注解，并接下来获取该注解上的属性
		// Update ann = annotation.getInstance();
		// java.util.List<Object> extensionProviders = annotation.getActualExpressions("value");
		String beanClassName = null;
		String dtoClassName = null;
		Iterator<JCExpression> iterator = ast.args.iterator();
		while (iterator.hasNext()) {
			JCAssign next = (JCAssign) iterator.next();
			if (next.lhs.toString().equalsIgnoreCase("value")) {
				beanClassName = ((JCFieldAccess) next.rhs).type.getTypeArguments().toString();
			} else if (next.lhs.toString().equalsIgnoreCase("dto")) {
				dtoClassName = ((JCFieldAccess) next.rhs).type.getTypeArguments().toString();
			}

		}
		// 获取该注解的地方，如果注解在类上面，则获取该类，如果注解在方法上，则获取该方法，如此类推
		JavacNode typeNode = annotationNode.up();

		JCExpression extendsClause = ((JCClassDecl) typeNode.get()).getExtendsClause();
		List<JCExpression> arguments = ((JCTypeApply) extendsClause).arguments;
		String generaClass = arguments.last().type.toString();
		if(beanClassName == null || beanClassName.equals("")) {
			if(generaClass == null || generaClass.equals("")) {
				throw new NullPointerException("必须要有 value 或 父类泛型 两者之一。");
			}
			beanClassName = generaClass;
		}

		generateUpdate(typeNode, annotationNode, beanClassName, dtoClassName, true);
	}

	public void generateUpdate(JavacNode typeNode, JavacNode source, String beanClassName, String dtoClassName, boolean whineIfExists) {
		boolean notAClass = true;
		if (typeNode.get() instanceof JCClassDecl) {
			long flags = ((JCClassDecl) typeNode.get()).mods.flags;
			notAClass = (flags & (Flags.INTERFACE | Flags.ANNOTATION)) != 0;
		}

		if (notAClass) {
			source.addError("@Update is only supported on a class or enum.");
			return;
		}

		boolean isExistsBefore;
		switch (methodExists(Before_Name, typeNode, 1)) {
			case NOT_EXISTS:
				JCMethodDecl method = createUpdateBefore(typeNode, beanClassName, source.get());
				injectMethod(typeNode, method);
				isExistsBefore = true;
				break;
			case EXISTS_BY_LOMBOK:
				isExistsBefore = true;
				break;
			case EXISTS_BY_USER:
				isExistsBefore = true;
				if (whineIfExists) {
					source.addWarning("Not generating update(): A method with that name already exists");
				}
				break;
			default:
				isExistsBefore = true;
		}


		boolean isExistsAfter;
		switch (methodExists(After_Name, typeNode, 1)) {
			case NOT_EXISTS:
				JCMethodDecl method = createUpdateAfter(typeNode, beanClassName, source.get());
				injectMethod(typeNode, method);
				isExistsAfter = true;
				break;
			case EXISTS_BY_LOMBOK:
				isExistsAfter = true;
				break;
			case EXISTS_BY_USER:
				isExistsAfter = true;
				if (whineIfExists) {
					source.addWarning("Not generating update(): A method with that name already exists");
				}
				break;
			default:
				isExistsAfter = true;
		}

		switch (methodExists(Module_Key, typeNode, 1)) {
			case NOT_EXISTS:
				JCMethodDecl method = createUpdate(typeNode, beanClassName, dtoClassName, isExistsBefore, isExistsAfter, source.get());
				injectMethod(typeNode, method);
				break;
			case EXISTS_BY_LOMBOK:
				break;
			default:
			case EXISTS_BY_USER:
				if (whineIfExists) {
					source.addWarning("Not generating update(): A method with that name already exists");
				}
				break;
		}
	}


	static JCMethodDecl createUpdateBefore(JavacNode typeNode, String beanClassName, JCTree source) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		Name updateBeforeName = typeNode.toName(Before_Name);
		Name objName = typeNode.toName(Method_Arg_Name);

		// B
		JCExpression keyType = chainDotsString(typeNode, beanClassName);
		// B obj
		JCVariableDecl obj = maker.VarDef(maker.Modifiers(Flags.PARAMETER), objName, keyType, null);
		// private
		JCModifiers modifiers = maker.Modifiers(Flags.PRIVATE);
		// String
		JCExpression beforeType = genTypeRef(typeNode, "java.lang.String");

		// ""
		JCLiteral aTure = maker.Literal("");
		// return ""
		JCStatement returnStatement = maker.Return(aTure);

		// { return ""; }
		JCBlock block = maker.Block(0, List.<JCStatement>of(returnStatement));
		// private String updateBefore(C obj) { return ""; }
		JCMethodDecl updateBeforeMethod = maker.MethodDef(modifiers, updateBeforeName, beforeType,
				List.<JCTypeParameter>nil(), List.of(obj), List.<JCExpression>nil(), block, null);
		return recursiveSetGeneratedBy(updateBeforeMethod, source, typeNode.getContext());
	}

	static JCMethodDecl createUpdateAfter(JavacNode typeNode, String beanClassName, JCTree source) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		Name updateAfterName = typeNode.toName(After_Name);
		Name objName = typeNode.toName(Method_Arg_Name);

		// B
		JCExpression keyType = chainDotsString(typeNode, beanClassName);
		// B obj
		JCVariableDecl obj = maker.VarDef(maker.Modifiers(Flags.PARAMETER), objName, keyType, null);
		// protected
		JCModifiers modifiers = maker.Modifiers(Flags.PROTECTED);
		// void
		JCExpression afterType = maker.Type(createVoidType(typeNode.getTreeMaker(), CTC_VOID));
		// {}
		JCBlock block = maker.Block(0, List.<JCStatement>nil());
		// protected void updateAfter(C obj) {}
		JCMethodDecl updateAfterMethod = maker.MethodDef(modifiers, updateAfterName, afterType,
				List.<JCTypeParameter>nil(), List.of(obj), List.<JCExpression>nil(), block, null);
		return recursiveSetGeneratedBy(updateAfterMethod, source, typeNode.getContext());
	}


	static JCMethodDecl createUpdate(JavacNode typeNode, String beanClassName, String dtoClassName, boolean isExistsBefore, boolean isExistsAfter, JCTree source) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		ListBuffer bodyBlockList = new ListBuffer();
		JCBlock body;
		Name updateBeforeName = typeNode.toName(Before_Name);
		Name updateAfterName = typeNode.toName(After_Name);

        /*
            private boolean updateBefore(T obj) { return true; }
            private void updateAfter(T obj) {}

            @ApiOperation(value = Module_Discript, notes = Module_Discript, httpMethod = "POST", produces = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            @ApiResponse(code = Msg.SUCCESS_CODE, message = "新增成功", response = Msg.class)
            @ApiImplicitParam(value = "实体", required = true, name = "obj", paramType = "body")
            @PostMapping("update")
            public Msg update(@Validated(value = Update.class) T.VO t) {
                T obj = new T()
                BeanUtils.copyPropertys(t, obj);
                String msg = updateBefore(t);
                if(msg != null && !"".equals(msg)) {
                   return Msg.ok(msg);
                } else {
	                Ebean.update(obj);
	                updateAfter(obj);
	                return Msg.ok("新增成功");
                }
            }
        * */
		// 添加一个 注解
		JCExpression annotationArg = maker.Literal(Module_Key); // 序列成一个字符串
		JCAnnotation overrideAnnotation = maker.Annotation(genTypeRef(typeNode, "org.springframework.web.bind.annotation.PostMapping"), List.of(annotationArg));
		// 添加第二个 注解
		JCExpression annotationArg21 = maker.Assign(maker.Ident(typeNode.toName("value")), maker.Literal("实体"));
		JCExpression annotationArg22 = maker.Assign(maker.Ident(typeNode.toName("paramType")), maker.Literal("body"));
		JCExpression annotationArg23 = maker.Assign(maker.Ident(typeNode.toName("name")), maker.Literal("obj"));
		JCExpression annotationArg24 = maker.Assign(maker.Ident(typeNode.toName("required")), maker.Literal(CTC_BOOLEAN, 1));
		JCAnnotation overrideAnnotation2 = maker.Annotation(genTypeRef(typeNode, "io.swagger.annotations.ApiImplicitParam"),
				List.of(annotationArg21, annotationArg22, annotationArg23, annotationArg24));
		// 添加第三个 注解
		JCExpression annotationArg31 = maker.Assign(maker.Ident(typeNode.toName("value")), maker.Literal(Module_Discript));
		JCExpression annotationArg32 = maker.Assign(maker.Ident(typeNode.toName("notes")), maker.Literal(Module_Discript));
		JCExpression annotationArg33 = maker.Assign(maker.Ident(typeNode.toName("httpMethod")), maker.Literal("POST"));
		JCAnnotation overrideAnnotation3 = maker.Annotation(genTypeRef(typeNode, "io.swagger.annotations.ApiOperation"),
				List.of(annotationArg31, annotationArg32, annotationArg33));
		// 添加第四个 注解
		JCExpression annotationArg41 = maker.Assign(maker.Ident(typeNode.toName("code")), genTypeRef(typeNode, "com.zyf.result.Msg.SUCCESS_CODE"));
		JCExpression annotationArg42 = maker.Assign(maker.Ident(typeNode.toName("message")), maker.Literal(Module_Discript + "成功"));
		JCExpression annotationArg43 = maker.Assign(maker.Ident(typeNode.toName("response")), genTypeRef(typeNode, "com.zyf.result.Msg.class"));
		JCAnnotation overrideAnnotation4 = maker.Annotation(genTypeRef(typeNode, "io.swagger.annotations.ApiResponse"),
				List.of(annotationArg41, annotationArg42, annotationArg43));

		// 附加注解到方法上
		JCModifiers methodAccess = maker.Modifiers(Flags.PUBLIC, List.of(overrideAnnotation, overrideAnnotation2, overrideAnnotation3, overrideAnnotation4));
		// 设定返回值类型
		JCExpression methodReturnType = genTypeRef(typeNode, "com.zyf.result.Msg");

		JCExpression returnRightBodyMsg = maker.Literal(Module_Discript + "成功");
		JCExpression returnRightBody = chainDots(typeNode, "com", "zyf", "result", "Msg", "ok");
		JCExpression returnRightBodyEnd = maker.Apply(List.<JCExpression>nil(), returnRightBody, List.of(returnRightBodyMsg));
		JCStatement methodReturnBodyEndLine = maker.Return(returnRightBodyEnd);

		Name objName = typeNode.toName(Method_Arg_Name);

		if(dtoClassName != null && !dtoClassName.equals("")) {
			// Entity.B
			JCExpression newClassLeft = chainDotsString(typeNode, beanClassName);
			// new Entity.B()
			JCNewClass newClassRight = maker.NewClass(null, List.nil(), newClassLeft, List.nil(), null);
			// Entity.B obj = new Entity.B();
			JCVariableDecl newClassEnd = maker.VarDef(maker.Modifiers(0),
					typeNode.toName(Method_Arg_Name), newClassLeft, newClassRight);

			// org.springframework.beans.BeanUtils
			JCExpression beanUtilsClass = chainDotsString(typeNode, "org.springframework.beans.BeanUtils");
			// org.springframework.beans.BeanUtils.copyProperties
			JCFieldAccess copyPropertiesMethodLeft = maker.Select(beanUtilsClass, typeNode.toName("copyProperties"));
			// BeanUtils.copyProperties(obj, t)
			JCMethodInvocation copyPropertiesRight = maker.Apply(List.nil(), copyPropertiesMethodLeft,
					List.of((maker.Ident(typeNode.toName("t"))), maker.Ident(typeNode.toName("obj"))));
			// BeanUtils.copyProperties(obj, t);
			JCStatement copyPropertiesEnd = maker.Exec(copyPropertiesRight);

			bodyBlockList.append(newClassEnd);
			bodyBlockList.append(copyPropertiesEnd);
		}

		// B
		JCExpression methodParamLeft = chainDotsString(typeNode, beanClassName);
		// 添加一个 注解 @org.springframework.validation.annotation.Validated({com.zyf.valid.Update.class})
		JCExpression methodParamAnnotationLeft = genTypeRef(typeNode, "com.zyf.valid.Update.class");
		JCAnnotation methodParamAnnotationRight = maker.Annotation(genTypeRef(typeNode, "org.springframework.validation.annotation.Validated"), List.of(methodParamAnnotationLeft));
		// B
		JCVariableDecl methodParamEnd = maker.VarDef(maker.Modifiers(Flags.PARAMETER,
				List.<JCAnnotation>of(methodParamAnnotationRight)), objName, methodParamLeft, null);
		// B.VO obj
		if(dtoClassName != null && !dtoClassName.equals("")){
			JCExpression dtoClassType = chainDotsString(typeNode, dtoClassName);
			methodParamEnd = maker.VarDef(maker.Modifiers(Flags.PARAMETER,
					List.<JCAnnotation>of(methodParamAnnotationRight)), typeNode.toName("t"), dtoClassType, null);
		}

		// updateBefore
		JCExpression beforeMethodLeft = maker.Ident(updateBeforeName);
		// obj
		JCExpression beforeMethodArgs = maker.Ident(objName);
		// String
		JCExpression beforeMethodReturnTypeLeft = genTypeRef(typeNode, "java.lang.String");
		// String msg = updateBefore(obj)
		JCVariableDecl beforeMethodEnd = maker.VarDef(maker.Modifiers(0), typeNode.toName("msg"),
				beforeMethodReturnTypeLeft, maker.Apply(List.<JCExpression>nil(), beforeMethodLeft, List.of(beforeMethodArgs)));

		// !"".equals(msg)
		JCMethodInvocation callEqMethodRight = maker.Apply(List.<JCExpression>nil(),
				maker.Select(maker.Literal(""), typeNode.toName("equals")),
				List.<JCExpression>of(maker.Ident(typeNode.toName("msg"))));
		JCUnary callEqMethodLeft = maker.Unary(CTC_NOT, callEqMethodRight);
		// msg != null
		JCBinary notNull = maker.Binary(CTC_NOT_EQUAL,
				maker.Ident(typeNode.toName("msg")), maker.Literal(CTC_BOT, null));
		// msg != null && !"".equals(msg)
		JCBinary ifAnd = maker.Binary(JavacTreeMaker.TreeTag.treeTag("AND"), notNull, callEqMethodLeft);

		JCExpression ifBodyMsg = maker.Ident(beforeMethodEnd.getName());
		JCExpression ifBodyReturnTypeLeft = chainDots(typeNode, "com", "zyf", "result", "Msg", "ok");
		JCExpression ifBodyMsgEnd = maker.Apply(List.<JCExpression>nil(), ifBodyReturnTypeLeft, List.of(ifBodyMsg));
		JCStatement ifReturnBody = maker.Return(ifBodyMsgEnd);

		// if(msg != null && !"".equals(msg)) {}
		JCIf ifEnd = maker.If(ifAnd, maker.Block(0, List.<JCStatement>of(ifReturnBody)), null);

		// updateAfter
		JCExpression afterMethodLeft = maker.Ident(updateAfterName);
		// obj
		JCExpression afterMethodArgs = maker.Ident(objName);
		// updateAfter(obj);
		JCStatement afterMethodEnd = maker.Exec(maker.Apply(List.<JCExpression>nil(), afterMethodLeft, List.of(afterMethodArgs)));

		// Ebean.update
		JCExpression objMethod = chainDots(typeNode, "io", "ebean", "Ebean", "update");
		// Ebean.update
		JCExpression objArgs = maker.Ident(objName);
		// Ebean.update(obj)
		JCExpression objMethodSuccess = maker.Apply(List.<JCExpression>nil(), objMethod, List.<JCExpression>of(objArgs));
		// Ebean.update(obj);
		JCStatement objStatement = maker.Exec(objMethodSuccess);

		bodyBlockList.append(beforeMethodEnd);
		bodyBlockList.append(ifEnd);
		bodyBlockList.append(objStatement);
		bodyBlockList.append(afterMethodEnd);
		bodyBlockList.append(methodReturnBodyEndLine);

		body = maker.Block(0, bodyBlockList.toList());

		// method
		JCMethodDecl update = maker.MethodDef(methodAccess, typeNode.toName(Module_Key), methodReturnType,
				List.<JCTypeParameter>nil(), List.of(methodParamEnd), List.<JCExpression>nil(), body, null);
		return recursiveSetGeneratedBy(update, source, typeNode.getContext());
	}
}
