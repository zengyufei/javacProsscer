package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCIf;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCUnary;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import lombok.Insert;
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
public class HandleInsert extends JavacAnnotationHandler<Insert> {

	private static final String Before_Method_Name = "insertBefore";
	private static final String After_Method_Name = "insertAfter";
	private static final String Module_Discript = "新增";
	private static final String Method_Arg_Name = "obj";
	private static final String Method_Name = "insert";

	@Override
	public void handle(AnnotationValues<Insert> annotation, JCAnnotation ast, JavacNode annotationNode) {
		// annotationNode 是上下文
		// 判断是否已经使用了注解
		// handleFlagUsage(annotationNode, ConfigurationKeys.TO_STRING_FLAG_USAGE, "@Insert");

		// 如果存在则删除
		deleteAnnotationIfNeccessary(annotationNode, Insert.class);

		// 获取该注解，并接下来获取该注解上的属性
		// Insert ann = annotation.getInstance();
		// java.util.List<Object> extensionProviders = annotation.getActualExpressions("value");
		String beanClassName = null;
		String dtoClassName = null;
		boolean isRollback = false;
		Iterator<JCExpression> iterator = ast.args.iterator();
		while (iterator.hasNext()) {
			JCAssign next = (JCAssign) iterator.next();
			if (next.lhs.toString().equalsIgnoreCase("value")) {
				String className = next.rhs.toString();
				String beanName = className.substring(0, className.lastIndexOf("."));
				beanClassName = beanName;
			} else if (next.lhs.toString().equalsIgnoreCase("vo")) {
				String className = next.rhs.toString();
				String beanName = className.substring(0, className.lastIndexOf("."));
				dtoClassName = beanName;
			} else if (next.lhs.toString().equalsIgnoreCase("rollback")) {
				isRollback = true;
			}
		}
		// 获取该注解的地方，如果注解在类上面，则获取该类，如果注解在方法上，则获取该方法，如此类推
		JavacNode typeNode = annotationNode.up();
		try {
			generateInsert(typeNode, annotationNode, beanClassName, dtoClassName, isRollback, false);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void generateInsert(JavacNode typeNode, JavacNode source, String beanClassName, String dtoClassName, boolean isRollback, boolean whineIfExists) {
		boolean notAClass = true;
		if (typeNode.get() instanceof JCClassDecl) {
			long flags = ((JCClassDecl) typeNode.get()).mods.flags;
			notAClass = (flags & (Flags.INTERFACE | Flags.ANNOTATION)) != 0;
		}

		if (notAClass) {
			source.addError("@Insert is only supported on a class or enum.");
			return;
		}

		boolean isExistsBefore;
		switch (methodExists(Before_Method_Name, typeNode, 1)) {
			case NOT_EXISTS:
				JCMethodDecl method = createInsertBefore(typeNode, beanClassName, source.get());
				injectMethod(typeNode, method);
				isExistsBefore = true;
				break;
			case EXISTS_BY_LOMBOK:
				isExistsBefore = true;
				break;
			case EXISTS_BY_USER:
				isExistsBefore = true;
				if (whineIfExists) {
					source.addWarning("Not generating "+ Before_Method_Name +"(): A method with that name already exists");
				}
				break;
			default:
				isExistsBefore = true;
		}


		boolean isExistsAfter;
		switch (methodExists(After_Method_Name, typeNode, 1)) {
			case NOT_EXISTS:
				JCMethodDecl method = createInsertAfter(typeNode, beanClassName, source.get());
				injectMethod(typeNode, method);
				isExistsAfter = true;
				break;
			case EXISTS_BY_LOMBOK:
				isExistsAfter = true;
				break;
			case EXISTS_BY_USER:
				isExistsAfter = true;
				if (whineIfExists) {
					source.addWarning("Not generating "+ After_Method_Name +"(): A method with that name already exists");
				}
				break;
			default:
				isExistsAfter = true;
		}

		switch (methodExists(Method_Name, typeNode, 1)) {
			case NOT_EXISTS:
				JCMethodDecl method = createInsert(typeNode, beanClassName, dtoClassName, isExistsBefore, isExistsAfter, isRollback, source.get());
				injectMethod(typeNode, method);
				break;
			case EXISTS_BY_LOMBOK:
				break;
			default:
			case EXISTS_BY_USER:
				if (whineIfExists) {
					source.addWarning("Not generating "+ Method_Name +"(): A method with that name already exists");
				}
				break;
		}
	}


	static JCMethodDecl createInsertBefore(JavacNode typeNode, String beanClassName, JCTree source) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		Name insertBeforeName = typeNode.toName(Before_Method_Name);
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
		// private String insertBefore(C obj) { return ""; }
		JCMethodDecl insertBeforeMethod = maker.MethodDef(modifiers, insertBeforeName, beforeType,
				List.<JCTypeParameter>nil(), List.of(obj), List.<JCExpression>nil(), block, null);
		return recursiveSetGeneratedBy(insertBeforeMethod, source, typeNode.getContext());
	}

	static JCMethodDecl createInsertAfter(JavacNode typeNode, String beanClassName, JCTree source) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		Name insertAfterName = typeNode.toName(After_Method_Name);
		Name objName = typeNode.toName(Method_Arg_Name);

		// B
		JCExpression keyType = chainDotsString(typeNode, beanClassName);
		// B obj
		JCVariableDecl obj = maker.VarDef(maker.Modifiers(Flags.PARAMETER), objName, keyType, null);
		// protected
		JCModifiers modifiers = maker.Modifiers(Flags.PROTECTED);
		// void
		JCExpression afterType = maker.Type(createVoidType(typeNode.getSymbolTable(), CTC_VOID));
		// {}
		JCBlock block = maker.Block(0, List.<JCStatement>nil());
		// protected void insertAfter(C obj) {}
		JCMethodDecl insertAfterMethod = maker.MethodDef(modifiers, insertAfterName, afterType,
				List.<JCTypeParameter>nil(), List.of(obj), List.<JCExpression>nil(), block, null);
		return recursiveSetGeneratedBy(insertAfterMethod, source, typeNode.getContext());
	}


	static JCMethodDecl createInsert(JavacNode typeNode, String beanClassName, String dtoClassName, boolean isExistsBefore, boolean isExistsAfter, boolean isRollback, JCTree source) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		ListBuffer bodyBlockList = new ListBuffer();
		JCBlock body;
		Name insertBeforeName = typeNode.toName(Before_Method_Name);
		Name insertAfterName = typeNode.toName(After_Method_Name);

        /*
            private boolean insertBefore(T obj) { return true; }
            private void insertAfter(T obj) {}

            @ApiOperation(value = Module_Discript, notes = Module_Discript, httpMethod = "POST", produces = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            @ApiResponse(code = Msg.SUCCESS_CODE, message = "新增成功", response = Msg.class)
            @ApiImplicitParam(value = "实体", required = true, name = "obj", paramType = "body")
            @PostMapping("insert")
            public Msg insert(@Validated(value = Insert.class) T.VO t) {
                T obj = new T()
                BeanUtils.copyPropertys(t, obj);
                String msg = insertBefore(t);
                if(msg != null && !"".equals(msg)) {
                   return Msg.ok(msg);
                } else {
	                Ebean.insert(obj);
	                insertAfter(obj);
	                return Msg.ok("新增成功");
                }
            }
        * */
		ListBuffer methodAnnotations = new ListBuffer();
		// 添加一个 注解
		JCExpression annotationArg = maker.Literal(Method_Name); // 序列成一个字符串
		JCAnnotation overrideAnnotation = maker.Annotation(
				genTypeRef(typeNode, "org.springframework.web.bind.annotation.PostMapping"),
				List.of(annotationArg));
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

		methodAnnotations.append(overrideAnnotation);
		methodAnnotations.append(overrideAnnotation2);
		methodAnnotations.append(overrideAnnotation3);
		methodAnnotations.append(overrideAnnotation4);

		if(isRollback) {
			JCAnnotation overrideRollBackAnnotation = maker.Annotation(genTypeRef(typeNode,
					"io.ebean.annotation.Transactional"), List.<JCExpression>nil());
			methodAnnotations.append(overrideRollBackAnnotation);
		}

		// 附加注解到方法上
		JCModifiers methodAccess = maker.Modifiers(Flags.PUBLIC,
				methodAnnotations.toList());
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
			JCNewClass newClassRight = maker.NewClass(null, List.<JCExpression>nil(),
					newClassLeft, List.<JCExpression>nil(), null);
			// Entity.B obj = new Entity.B();
			JCVariableDecl newClassEnd = maker.VarDef(maker.Modifiers(0),
					typeNode.toName(Method_Arg_Name), newClassLeft, newClassRight);

			// org.springframework.beans.BeanUtils
			JCExpression beanUtilsClass = chainDotsString(typeNode, "org.springframework.beans.BeanUtils");
			// org.springframework.beans.BeanUtils.copyProperties
			JCFieldAccess copyPropertiesMethodLeft = maker.Select(beanUtilsClass, typeNode.toName("copyProperties"));
			// BeanUtils.copyProperties(obj, t)
			JCMethodInvocation copyPropertiesRight = maker.Apply(List.<JCExpression>nil(), copyPropertiesMethodLeft,
					List.<JCExpression>of((maker.Ident(typeNode.toName("t"))), maker.Ident(typeNode.toName("obj"))));
			// BeanUtils.copyProperties(obj, t);
			JCStatement copyPropertiesEnd = maker.Exec(copyPropertiesRight);

			bodyBlockList.append(newClassEnd);
			bodyBlockList.append(copyPropertiesEnd);
		}

		// B
		JCExpression methodParamLeft = chainDotsString(typeNode, beanClassName);
		// 添加一个 注解 @org.springframework.validation.annotation.Validated({com.zyf.valid.Insert.class})
		JCExpression methodParamAnnotationLeft = genTypeRef(typeNode, "com.zyf.valid.Insert.class");
		JCAnnotation methodParamAnnotationRight = maker.Annotation(genTypeRef(typeNode,
				"org.springframework.validation.annotation.Validated"),
				List.of(methodParamAnnotationLeft));
		// B
		JCVariableDecl methodParamEnd = maker.VarDef(maker.Modifiers(Flags.PARAMETER,
				List.<JCAnnotation>of(methodParamAnnotationRight)), objName, methodParamLeft, null);
		// B.VO obj
		if(dtoClassName != null && !dtoClassName.equals("")){
			JCExpression dtoClassType = chainDotsString(typeNode, dtoClassName);
			methodParamEnd = maker.VarDef(maker.Modifiers(Flags.PARAMETER,
					List.<JCAnnotation>of(methodParamAnnotationRight)), typeNode.toName("t"), dtoClassType, null);
		}

		// insertBefore
		JCExpression beforeMethodLeft = maker.Ident(insertBeforeName);
		// obj
		JCExpression beforeMethodArgs = maker.Ident(objName);
		// String
		JCExpression beforeMethodReturnTypeLeft = genTypeRef(typeNode, "java.lang.String");
		// String msg = insertBefore(obj)
		JCVariableDecl beforeMethodEnd = maker.VarDef(maker.Modifiers(0), typeNode.toName("msg"),
				beforeMethodReturnTypeLeft, maker.Apply(List.<JCExpression>nil(),
						beforeMethodLeft, List.of(beforeMethodArgs)));

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

		// insertAfter
		JCExpression afterMethodLeft = maker.Ident(insertAfterName);
		// obj
		JCExpression afterMethodArgs = maker.Ident(objName);
		// insertAfter(obj);
		JCStatement afterMethodEnd = maker.Exec(maker.Apply(List.<JCExpression>nil(), afterMethodLeft, List.of(afterMethodArgs)));

		// Ebean.insert
		JCExpression objMethod = chainDots(typeNode, "io", "ebean", "Ebean", "insert");
		// Ebean.insert
		JCExpression objArgs = maker.Ident(objName);
		// Ebean.insert(obj)
		JCExpression objMethodSuccess = maker.Apply(List.<JCExpression>nil(), objMethod, List.<JCExpression>of(objArgs));
		// Ebean.insert(obj);
		JCStatement objStatement = maker.Exec(objMethodSuccess);

		bodyBlockList.append(beforeMethodEnd);
		bodyBlockList.append(ifEnd);
		bodyBlockList.append(objStatement);
		bodyBlockList.append(afterMethodEnd);
		bodyBlockList.append(methodReturnBodyEndLine);

		body = maker.Block(0, bodyBlockList.toList());

		// method
		JCMethodDecl insert = maker.MethodDef(methodAccess, typeNode.toName(Method_Name), methodReturnType,
				List.<JCTypeParameter>nil(), List.of(methodParamEnd), List.<JCExpression>nil(), body, null);
		return recursiveSetGeneratedBy(insert, source, typeNode.getContext());
	}
}
