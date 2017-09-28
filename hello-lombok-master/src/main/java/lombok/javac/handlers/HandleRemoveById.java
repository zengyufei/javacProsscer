package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import lombok.RemoveById;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import org.mangosdk.spi.ProviderFor;

import static lombok.javac.Javac.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * Handles the {@code RemoveById} annotation for javac.
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleRemoveById extends JavacAnnotationHandler<RemoveById> {

    private static final String Before_Name = "removeByIdBefore";
    private static final String After_Name = "removeByIdAfter";
    private static final String Module_Discript = "移除";
    private static final String Method_Arg_Name = "id";
    private static final String Module_Key = "removeById";

    @Override
    public void handle(AnnotationValues<RemoveById> annotation, JCAnnotation ast, JavacNode annotationNode) {
        // annotationNode 是上下文
        // 判断是否已经使用了注解
        // handleFlagUsage(annotationNode, ConfigurationKeys.TO_STRING_FLAG_USAGE, "@RemoveById");

        // 如果存在则删除
        deleteAnnotationIfNeccessary(annotationNode, RemoveById.class);

        // java.util.List<Object> extensionProviders = annotation.getActualExpressions("value");
        // String beanClassName = ((JCAssign) ast.args.last()).rhs.type.getTypeArguments().last().toString();
        String className = ((JCAssign) ast.args.last()).rhs.toString();
        String beanClassName = className.substring(0, className.indexOf("."));
        // 获取该注解的地方，如果注解在类上面，则获取该类，如果注解在方法上，则获取该方法，如此类推
        JavacNode typeNode = annotationNode.up();

        // 获取该注解，并接下来获取该注解上的属性
        RemoveById ann = annotation.getInstance();
        java.util.List<Object> extensionProviders = annotation.getActualExpressions("value");
        for (Object extensionProvider : extensionProviders) {
            if (!(extensionProvider instanceof JCFieldAccess)) continue;
            JCFieldAccess provider = (JCFieldAccess) extensionProvider;
            beanClassName = ((JCIdent) provider.selected).sym.toString();
        }

        generateRemoveById(typeNode, annotationNode, beanClassName, true);
    }

    public void generateRemoveById(JavacNode typeNode, JavacNode source, String beanClass, boolean whineIfExists) {
        boolean notAClass = true;
        if (typeNode.get() instanceof JCClassDecl) {
            long flags = ((JCClassDecl)typeNode.get()).mods.flags;
            notAClass = (flags & (Flags.INTERFACE | Flags.ANNOTATION)) != 0;
        }

        if (notAClass) {
            source.addError("@RemoveById is only supported on a class or enum.");
            return;
        }

        switch (methodExists(Module_Key, typeNode, 0)) {
            case NOT_EXISTS:
                JCMethodDecl method = createRemoveById(typeNode, beanClass, source.get());
                injectMethod(typeNode, method);
                break;
            case EXISTS_BY_LOMBOK:
                break;
            default:
            case EXISTS_BY_USER:
                if (whineIfExists) {
                    source.addWarning("Not generating removeById(): A method with that name already exists");
                }
                break;
        }
    }

    static JCMethodDecl createRemoveById(JavacNode typeNode, String beanClassName, JCTree source) {
        JavacTreeMaker maker = typeNode.getTreeMaker();
        List<JCTypeParameter> classGeneric = ((JCClassDecl) typeNode.get()).getTypeParameters();

        /*
            private void removeByIdBefore(T id) {}
            private void removeByIdAfter(T id) {}

            @ApiOperation(value = "删除", notes = "删除", httpMethod = "GET")
            @ApiResponse(code = Msg.SUCCESS_CODE, message = "删除成功", response = Msg.class)
            @ApiImplicitParam(value = "序号", required = true, name = "id", paramType = "query")
            @GetMapping("remoteById")
            public Msg remoteById(@NotNull(message = "Id 不能为空") @Min(value = 1, message = "Id 不能为空") Long id) {
                removeByIdBefore(id);
                Ebean.delete(SysOrg.class, id);
                removeByIdAfter(id)
                return Msg.ok("删除成功");
            }
        * */
        // 添加一个 注解
        JCExpression annotationArg =  maker.Literal(Module_Key); // 序列成一个字符串
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
        // 设定返回值类型
        JCExpression returnType = genTypeRef(typeNode, "com.zyf.result.Msg");

        JCExpression typeArg =  maker.Literal(Module_Discript + "成功");
        JCExpression tsMethod = chainDots(typeNode, "com", "zyf", "result", "Msg", "ok");
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
        //JCExpression keyType = chainDotsString(typeNode, beanClassName);

        JCExpression keyType = chainDotsString(typeNode, "java.lang.Long");

        // 添加一个 注解 @org.springframework.validation.annotation.Validated({com.zyf.valid.RemoveById.class})
        JCExpression validNoNullArg11 = maker.Assign(maker.Ident(typeNode.toName("message")), maker.Literal("Id 不能为空"));
        JCExpression validNoNullArg12 = maker.Assign(maker.Ident(typeNode.toName("value")), maker.Literal(CTC_INT,1));
        JCExpression validNoNullArg2 = maker.Assign(maker.Ident(typeNode.toName("message")), maker.Literal("Id 不能为空"));
        JCAnnotation validAnnotation1 = maker
                .Annotation(genTypeRef(typeNode, "javax.validation.constraints.Min"),
                List.of(validNoNullArg11, validNoNullArg12));

        JCAnnotation validAnnotation2 = maker
                .Annotation(genTypeRef(typeNode, "javax.validation.constraints.NotNull"),
                List.of(validNoNullArg2));
        // B obj
        JCVariableDecl obj = maker.VarDef(maker.Modifiers(Flags.PARAMETER,
                List.<JCAnnotation>of(validAnnotation1, validAnnotation2)), objName, keyType, null);

        JCBlock body;
        // name = removeByIdBefore

        Name removeByIdBeforeName = typeNode.toName(Before_Name);
        Name removeByIdAfterName = typeNode.toName(After_Name);

        // 判断 removeByIdAfter 方法是否存在
        MemberExistsResult removeByIdBefore = methodExists(Before_Name, typeNode, 1);
        if("NOT_EXISTS".equalsIgnoreCase(removeByIdBefore.name())){
            // protected
            JCModifiers modifiers = maker.Modifiers(Flags.PROTECTED);
            // void
            JCExpression beforeType = maker.Type(createVoidType(typeNode.getTreeMaker(), CTC_VOID));
            // {}
            JCBlock block = maker.Block(0, List.<JCStatement>nil());
            // protected void removeByIdBefore(C obj) {}
            JCMethodDecl removeByIdBeforeMethod = maker.MethodDef(modifiers, removeByIdBeforeName, beforeType,
                    List.<JCTypeParameter>nil(), List.of(obj), List.<JCExpression>nil(), block, null);
            injectMethod(typeNode, removeByIdBeforeMethod);
        }
        // 判断 removeByIdAfter 方法是否存在
        MemberExistsResult removeByIdAfter = methodExists(After_Name, typeNode, 1);
        if("NOT_EXISTS".equalsIgnoreCase(removeByIdAfter.name())){
            // protected
            JCModifiers modifiers = maker.Modifiers(Flags.PROTECTED);
            // void
            JCExpression afterType = maker.Type(createVoidType(typeNode.getTreeMaker(), CTC_VOID));
            // {}
            JCBlock block = maker.Block(0, List.<JCStatement>nil());
            // protected void removeByIdAfter(C obj) {}
            JCMethodDecl removeByIdAfterMethod = maker.MethodDef(modifiers, removeByIdAfterName, afterType,
                    List.<JCTypeParameter>nil(), List.of(obj), List.<JCExpression>nil(), block, null);
            injectMethod(typeNode, removeByIdAfterMethod);
        }


        // removeByIdBefore
        JCExpression beforeMethod = maker.Ident(removeByIdBeforeName);
        // obj
        JCExpression beforeArgs = maker.Ident(objName);
        // removeByIdBefore(obj)
        JCExpression beforeMethodSuccess = maker.Apply(List.<JCExpression>nil(), beforeMethod, List.of(beforeArgs));
        // removeByIdBefore(obj);
        JCStatement beforeStatement = maker.Exec(beforeMethodSuccess);

        // removeByIdAfter
        JCExpression afterMethod = maker.Ident(removeByIdAfterName);
        // obj
        JCExpression afterArgs = maker.Ident(objName);
        // removeByIdAfter(obj)
        JCExpression afterMethodSuccess = maker.Apply(List.<JCExpression>nil(), afterMethod, List.of(afterArgs));
        // removeByIdAfter(obj);
        JCStatement afterStatement = maker.Exec(afterMethodSuccess);


        // obj.removeById
        JCExpression objMethod = chainDots(typeNode, "io", "ebean", "Ebean", "delete");
        // bean class
        JCExpression beanClass = genTypeRef(typeNode, beanClassName+ ".class");
        // id
        JCExpression objArgs = maker.Ident(objName);
        // Ebean.delete(SysOrg.class, id)
        JCExpression objMethodSuccess = maker.Apply(List.<JCExpression>nil(), objMethod, List.<JCExpression>of(beanClass, objArgs));
        // removeByIdAfter(obj);
        JCStatement objStatement = maker.Exec(objMethodSuccess);

        body = maker.Block(0, List.of(beforeStatement, objStatement, afterStatement, returnStatement));

        genTypeRef(typeNode, "com.zyf.result.Msg");

        // method
        JCMethodDecl removeById = maker.MethodDef(mods, typeNode.toName(Module_Key), returnType,
                List.<JCTypeParameter>nil(), List.of(obj), List.<JCExpression>nil(), body, null);
        return recursiveSetGeneratedBy(removeById, source, typeNode.getContext());
    }
}
