package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCAssign;
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
import lombok.Insert;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import org.mangosdk.spi.ProviderFor;

import static lombok.javac.Javac.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * Handles the {@code Insert} annotation for javac.
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleInsert extends JavacAnnotationHandler<Insert> {

	private static final String Module_Key = "insert";
	private static final String Before_Name = "insertBefore";
	private static final String After_Name = "insertAfter";
	private static final String Module_Discript = "新增";
	private static final String Method_Arg_Name = "obj";
	private static final String Result_Type_Name = "com.zyf.result.Msg";

	@Override
	public void handle(AnnotationValues<Insert> annotation, JCAnnotation ast, JavacNode annotationNode) {
		// annotationNode 是上下文
		// 判断是否已经使用了注解
		// handleFlagUsage(annotationNode, ConfigurationKeys.TO_STRING_FLAG_USAGE, "@Insert");

		// 如果存在则删除
		deleteAnnotationIfNeccessary(annotationNode, Insert.class);

		// 获取该注解，并接下来获取该注解上的属性
		Insert ann = annotation.getInstance();
		// java.util.List<Object> extensionProviders = annotation.getActualExpressions("value");
		// String beanClassName = ((JCAssign) ast.args.last()).rhs.type.getTypeArguments().last().toString();
		String className = ((JCAssign) ast.args.last()).rhs.toString();
		String beanClassName = className.substring(0, className.indexOf("."));
		// 获取该注解的地方，如果注解在类上面，则获取该类，如果注解在方法上，则获取该方法，如此类推
		JavacNode typeNode = annotationNode.up();

		generateInsert(typeNode, annotationNode, beanClassName, true);
	}

	public void generateInsert(JavacNode typeNode, JavacNode source, String beanClass, boolean whineIfExists) {
		boolean notAClass = true;
		if (typeNode.get() instanceof JCClassDecl) {
			long flags = ((JCClassDecl)typeNode.get()).mods.flags;
			notAClass = (flags & (Flags.INTERFACE | Flags.ANNOTATION)) != 0;
		}

		if (notAClass) {
			source.addError("@Insert is only supported on a class or enum.");
			return;
		}

		switch (methodExists(Module_Key, typeNode, 0)) {
			case NOT_EXISTS:
				JCMethodDecl method = createInsert(typeNode, beanClass, source.get());
				injectMethod(typeNode, method);
				break;
			case EXISTS_BY_LOMBOK:
				break;
			default:
			case EXISTS_BY_USER:
				if (whineIfExists) {
					source.addWarning("Not generating insert(): A method with that name already exists");
				}
				break;
		}
	}

	static JCMethodDecl createInsert(JavacNode typeNode, String beanClassName, JCTree source) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		List<JCTypeParameter> classGeneric = ((JCClassDecl) typeNode.get()).getTypeParameters();

        /*
            private void insertBefore(T obj) {}
            private void insertAfter(T obj) {}

            @ApiOperation(value = Module_Discript, notes = Module_Discript, httpMethod = "POST", produces = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            @ApiResponse(code = Msg.SUCCESS_CODE, message = "新增成功", response = Msg.class)
            @ApiImplicitParam(value = "实体", required = true, name = "obj", paramType = "body")
            @PostMapping("insert")
            public Msg insert(@Validated(value = Insert.class) T obj) {
                insertBefore(obj);
                obj.insert();
                insertAfter(obj);
                return Msg.ok("新增成功");
            }
        * */
		// 添加一个 注解
		JCExpression annotationArg =  maker.Literal(Module_Key); // 序列成一个字符串
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
		JCExpression annotationArg41 = maker.Assign(maker.Ident(typeNode.toName("code")), genTypeRef(typeNode, Result_Type_Name + ".SUCCESS_CODE"));
		JCExpression annotationArg42 = maker.Assign(maker.Ident(typeNode.toName("message")), maker.Literal(Module_Discript + "成功"));
		JCExpression annotationArg43 = maker.Assign(maker.Ident(typeNode.toName("response")), genTypeRef(typeNode, Result_Type_Name + ".class"));
		JCAnnotation overrideAnnotation4 = maker.Annotation(genTypeRef(typeNode, "io.swagger.annotations.ApiResponse"),
				List.of(annotationArg41, annotationArg42, annotationArg43));

		// 附加注解到方法上
		JCModifiers mods = maker.Modifiers(Flags.PUBLIC,
				List.of(overrideAnnotation, overrideAnnotation2, overrideAnnotation3, overrideAnnotation4));
		// 设定返回值类型
		JCExpression returnType = genTypeRef(typeNode, Result_Type_Name);

		JCExpression typeArg =  maker.Literal(Module_Discript + "成功");
		JCExpression tsMethod = chainDots(typeNode, Result_Type_Name.split("."));
		JCExpression current = maker.Apply(List.<JCExpression>nil(), tsMethod, List.of(typeArg));
		JCStatement returnStatement = maker.Return(current);

		Name objName = typeNode.toName(Method_Arg_Name);

        /*
        // <C>
        JCTypeParameter jcTypeParameter = classGeneric.get(0);
        // C
        JCExpression keyType = chainDotsString(typeNode, jcTypeParameter.getName().toString());
        // C obj
        JCVariableDecl obj = maker.VarDef(maker.Modifiers(Flags.PARAMETER), objName, keyType, null);
        */

		// B
		JCExpression keyType = chainDotsString(typeNode, beanClassName);
		// 添加一个 注解 @org.springframework.validation.annotation.Validated({com.zyf.valid.Insert.class})
		JCExpression validGroup = genTypeRef(typeNode, "com.zyf.valid.Insert.class");
		JCAnnotation validAnnotation = maker.Annotation(genTypeRef(typeNode, "org.springframework.validation.annotation.Validated"), List.of(validGroup));
		// B obj
		JCVariableDecl obj = maker.VarDef(maker.Modifiers(Flags.PARAMETER, List.<JCAnnotation>of(validAnnotation)), objName, keyType, null);

		JCBlock body;
		// name = insertBefore

		Name insertBeforeName = typeNode.toName(Before_Name);
		Name insertAfterName = typeNode.toName(After_Name);

		// 判断 insertAfter 方法是否存在
		MemberExistsResult insertBefore = methodExists(Before_Name, typeNode, 1);
		if("NOT_EXISTS".equalsIgnoreCase(insertBefore.name())){
			// protected
			JCModifiers modifiers = maker.Modifiers(Flags.PROTECTED);
			// void
			JCExpression beforeType = maker.Type(createVoidType(typeNode.getTreeMaker(), CTC_VOID));
			// {}
			JCBlock block = maker.Block(0, List.<JCStatement>nil());
			// protected void insertBefore(C obj) {}
			JCMethodDecl insertBeforeMethod = maker.MethodDef(modifiers, insertBeforeName, beforeType,
					List.<JCTypeParameter>nil(), List.of(obj), List.<JCExpression>nil(), block, null);
			injectMethod(typeNode, insertBeforeMethod);
		}
		// 判断 insertAfter 方法是否存在
		MemberExistsResult insertAfter = methodExists(After_Name, typeNode, 1);
		if("NOT_EXISTS".equalsIgnoreCase(insertAfter.name())){
			// protected
			JCModifiers modifiers = maker.Modifiers(Flags.PROTECTED);
			// void
			JCExpression afterType = maker.Type(createVoidType(typeNode.getTreeMaker(), CTC_VOID));
			// {}
			JCBlock block = maker.Block(0, List.<JCStatement>nil());
			// protected void insertAfter(C obj) {}
			JCMethodDecl insertAfterMethod = maker.MethodDef(modifiers, insertAfterName, afterType,
					List.<JCTypeParameter>nil(), List.of(obj), List.<JCExpression>nil(), block, null);
			injectMethod(typeNode, insertAfterMethod);
		}


		// insertBefore
		JCExpression beforeMethod = maker.Ident(insertBeforeName);
		// obj
		JCExpression beforeArgs = maker.Ident(objName);
		// insertBefore(obj)
		JCExpression beforeMethodSuccess = maker.Apply(List.<JCExpression>nil(), beforeMethod, List.of(beforeArgs));
		// insertBefore(obj);
		JCStatement beforeStatement = maker.Exec(beforeMethodSuccess);

		// insertAfter
		JCExpression afterMethod = maker.Ident(insertAfterName);
		// obj
		JCExpression afterArgs = maker.Ident(objName);
		// insertAfter(obj)
		JCExpression afterMethodSuccess = maker.Apply(List.<JCExpression>nil(), afterMethod, List.of(afterArgs));
		// insertAfter(obj);
		JCStatement afterStatement = maker.Exec(afterMethodSuccess);


		// obj.insert
		JCExpression objMethod = chainDots(typeNode, "io", "ebean", "Ebean", "insert");
		// obj
		JCExpression objArgs = maker.Ident(objName);
		// obj.insert()
		JCExpression objMethodSuccess = maker.Apply(List.<JCExpression>nil(), objMethod, List.<JCExpression>of(objArgs));
		// insertAfter(obj);
		JCStatement objStatement = maker.Exec(objMethodSuccess);

		body = maker.Block(0, List.of(beforeStatement, objStatement, afterStatement, returnStatement));

		genTypeRef(typeNode, Result_Type_Name);

		// method
		JCMethodDecl insert = maker.MethodDef(mods, typeNode.toName(Module_Key), returnType,
				List.<JCTypeParameter>nil(), List.of(obj), List.<JCExpression>nil(), body, null);
		return recursiveSetGeneratedBy(insert, source, typeNode.getContext());
	}
}
