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
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import lombok.QueryAll;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import org.mangosdk.spi.ProviderFor;

import static lombok.javac.Javac.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * Handles the {@code QueryAll} annotation for javac.
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleQueryAll extends JavacAnnotationHandler<QueryAll> {

    private static final String Before_Name = "queryAllBefore";
    private static final String After_Name = "queryAllAfter";
    private static final String Module_Discript = "查询所有，不包括删除";
    private static final String Module_Key = "queryAll";

    @Override
    public void handle(AnnotationValues<QueryAll> annotation, JCAnnotation ast, JavacNode annotationNode) {
        // annotationNode 是上下文
        // 判断是否已经使用了注解
        // handleFlagUsage(annotationNode, ConfigurationKeys.TO_STRING_FLAG_USAGE, "@QueryAll");

        // 如果存在则删除
        deleteAnnotationIfNeccessary(annotationNode, QueryAll.class);

        // java.util.List<Object> extensionProviders = annotation.getActualExpressions("value");
        // String beanClassName = ((JCAssign) ast.args.last()).rhs.type.getTypeArguments().last().toString();
        String className = ((JCAssign) ast.args.last()).rhs.toString();
        String beanClassName = className.substring(0, className.indexOf("."));
        // 获取该注解的地方，如果注解在类上面，则获取该类，如果注解在方法上，则获取该方法，如此类推
        JavacNode typeNode = annotationNode.up();

        // 获取该注解，并接下来获取该注解上的属性
        java.util.List<Object> extensionProviders = annotation.getActualExpressions("value");
        for (Object extensionProvider : extensionProviders) {
            if (!(extensionProvider instanceof JCFieldAccess)) continue;
            JCFieldAccess provider = (JCFieldAccess) extensionProvider;
            beanClassName = ((JCIdent) provider.selected).sym.toString();
        }

        generateQueryAll(typeNode, annotationNode, beanClassName, true);
    }

    public void generateQueryAll(JavacNode typeNode, JavacNode source, String beanClass, boolean whineIfExists) {
        boolean notAClass = true;
        if (typeNode.get() instanceof JCClassDecl) {
            long flags = ((JCClassDecl)typeNode.get()).mods.flags;
            notAClass = (flags & (Flags.INTERFACE | Flags.ANNOTATION)) != 0;
        }

        if (notAClass) {
            source.addError("@QueryAll is only supported on a class or enum.");
            return;
        }

        switch (methodExists(Module_Key, typeNode, 0)) {
            case NOT_EXISTS:
                JCMethodDecl method = createQueryAll(typeNode, beanClass, source.get());
                injectMethod(typeNode, method);
                break;
            case EXISTS_BY_LOMBOK:
                break;
            default:
            case EXISTS_BY_USER:
                if (whineIfExists) {
                    source.addWarning("Not generating queryAll(): A method with that name already exists");
                }
                break;
        }
    }

    static JCMethodDecl createQueryAll(JavacNode typeNode, String beanClassName, JCTree source) {
        JavacTreeMaker maker = typeNode.getTreeMaker();
        List<JCTypeParameter> classGeneric = ((JCClassDecl) typeNode.get()).getTypeParameters();

        /*
            private void queryAllBefore(T id) {}
            private void queryAllAfter(T id) {}

           	@ApiOperation(value = "查询所有，不包括删除", notes = "查询单个，不包括删除", httpMethod = "GET", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
            @ApiResponses(value = {
                    @ApiResponse(code = Msg.SUCCESS_CODE, message = "查询成功", response = Msg.class),
                    @ApiResponse(code = Msg.ERROR_CODE, message = "系统错误", response = Msg.class)
            })
            @GetMapping("queryAll")
            public Msg queryAll() {
                return Msg.ok(Ebean.find(entityClass).findList());
            }
        * */
        // 添加一个 注解
        JCExpression annotationArg =  maker.Literal(Module_Key); // 序列成一个字符串
        JCAnnotation overrideAnnotation = maker.Annotation(genTypeRef(typeNode, "org.springframework.web.bind.annotation.GetMapping"), List.of(annotationArg));
        // 添加第二个 注解
        /*JCExpression annotationArg21 = maker.Assign(maker.Ident(typeNode.toName("value")), maker.Literal("查询所有，不包括删除"));
        JCExpression annotationArg22 = maker.Assign(maker.Ident(typeNode.toName("paramType")), maker.Literal("query"));
        JCExpression annotationArg23 = maker.Assign(maker.Ident(typeNode.toName("name")), maker.Literal(Method_Arg_Name));
        JCExpression annotationArg24 = maker.Assign(maker.Ident(typeNode.toName("required")), maker.Literal(CTC_BOOLEAN, 1));
        JCAnnotation overrideAnnotation2 = maker.Annotation(genTypeRef(typeNode, "io.swagger.annotations.ApiImplicitParam"),
                List.of(annotationArg21, annotationArg22, annotationArg23, annotationArg24));*/
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
        JCModifiers mods = maker.Modifiers(Flags.PUBLIC, List.of(overrideAnnotation, overrideAnnotation3, overrideAnnotation4));

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

        // 添加一个 注解 @org.springframework.validation.annotation.Validated({com.zyf.valid.QueryAll.class})
        JCBlock body;
        // name = queryAllBefore

        Name queryAllBeforeName = typeNode.toName(Before_Name);
        Name queryAllAfterName = typeNode.toName(After_Name);

        // 判断 queryAllAfter 方法是否存在
        MemberExistsResult queryAllBefore = methodExists(Before_Name, typeNode, 1);
        if("NOT_EXISTS".equalsIgnoreCase(queryAllBefore.name())){
            // protected
            JCModifiers modifiers = maker.Modifiers(Flags.PROTECTED);
            // void
            JCExpression beforeType = maker.Type(createVoidType(typeNode.getTreeMaker(), CTC_VOID));
            // {}
            JCBlock block = maker.Block(0, List.<JCStatement>nil());
            // protected void queryAllBefore(C obj) {}
            JCMethodDecl queryAllBeforeMethod = maker.MethodDef(modifiers, queryAllBeforeName, beforeType,
                    List.<JCTypeParameter>nil(), List.<JCVariableDecl>nil(), List.<JCExpression>nil(), block, null);
            injectMethod(typeNode, queryAllBeforeMethod);
        }
        // 判断 queryAllAfter 方法是否存在
        MemberExistsResult queryAllAfter = methodExists(After_Name, typeNode, 1);
        if("NOT_EXISTS".equalsIgnoreCase(queryAllAfter.name())){
            // protected
            JCModifiers modifiers = maker.Modifiers(Flags.PROTECTED);
            // void
            JCExpression afterType = maker.Type(createVoidType(typeNode.getTreeMaker(), CTC_VOID));
            // {}
            JCBlock block = maker.Block(0, List.<JCStatement>nil());
            // protected void queryAllAfter(C obj) {}
            JCMethodDecl queryAllAfterMethod = maker.MethodDef(modifiers, queryAllAfterName, afterType,
                    List.<JCTypeParameter>nil(), List.<JCVariableDecl>nil(), List.<JCExpression>nil(), block, null);
            injectMethod(typeNode, queryAllAfterMethod);
        }


        // queryAllBefore
        JCExpression beforeMethod = maker.Ident(queryAllBeforeName);
        // queryAllBefore(obj)
        JCExpression beforeMethodSuccess = maker.Apply(List.<JCExpression>nil(), beforeMethod, List.nil());
        // queryAllBefore(obj);
        JCStatement beforeStatement = maker.Exec(beforeMethodSuccess);

        // queryAllAfter
        JCExpression afterMethod = maker.Ident(queryAllAfterName);
        // queryAllAfter(obj)
        JCExpression afterMethodSuccess = maker.Apply(List.<JCExpression>nil(), afterMethod, List.nil());
        // queryAllAfter(obj);
        JCStatement afterStatement = maker.Exec(afterMethodSuccess);


        // obj.queryAll
        JCExpression objMethod = chainDots(typeNode, "io", "ebean", "Ebean", "find");
        // bean class
        JCExpression beanClass = genTypeRef(typeNode, beanClassName+ ".class");
        // Ebean.find(SysOrg.class)
        JCExpression objMethodSuccess = maker.Apply(List.<JCExpression>nil(), objMethod, List.<JCExpression>of(beanClass));
        // Ebean.find(SysOrg.class).findList
        JCExpression findList = maker.Select(objMethodSuccess, typeNode.toName("findList"));
        // Ebean.find(SysOrg.class).findList()
        JCExpression findListEnd = maker.Apply(List.<JCExpression>nil(), findList, List.<JCExpression>nil());
        // java.util.List
        JCExpression argLeft = chainDotsString(typeNode, "java.util.List");
        // java.util.List<SysOrg>
        JCExpression argLeftEnd = maker.TypeApply(argLeft, List.<JCExpression>of(chainDotsString(typeNode, beanClassName)));
        // java.util.List<SysOrg> = Ebean.find(SysOrg.class).findList();
        JCVariableDecl listLeft = maker.VarDef(maker.Modifiers(Flags.PARAMETER), typeNode.toName("list"), argLeftEnd, findListEnd);

        // 设定返回值类型
        JCIdent list = maker.Ident(typeNode.toName("list"));
        JCExpression returnType = genTypeRef(typeNode, "com.zyf.result.Msg");
        JCExpression tsMethod = chainDots(typeNode, "com", "zyf", "result", "Msg", "ok");
        JCExpression current = maker.Apply(List.<JCExpression>nil(), tsMethod, List.of(list));
        JCStatement returnStatement = maker.Return(current);

        body = maker.Block(0, List.of(beforeStatement, listLeft, afterStatement, returnStatement));

        genTypeRef(typeNode, "com.zyf.result.Msg");

        // method
        JCMethodDecl queryAll = maker.MethodDef(mods, typeNode.toName(Module_Key), returnType,
                List.<JCTypeParameter>nil(), List.<JCVariableDecl>nil(), List.<JCExpression>nil(), body, null);
        return recursiveSetGeneratedBy(queryAll, source, typeNode.getContext());
    }
}
