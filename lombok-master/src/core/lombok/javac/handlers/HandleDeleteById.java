package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCUnary;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCIf;
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
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import lombok.DeleteById;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import org.mangosdk.spi.ProviderFor;

import java.util.Iterator;

import static lombok.javac.Javac.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * Handles the {@code DeleteById} annotation for javac.
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleDeleteById extends JavacAnnotationHandler<DeleteById> {

    private static final String Before_Method_Name = "deleteByIdBefore";
    private static final String After_Method_Name = "deleteByIdAfter";
    private static final String Module_Discript = "移除";
    private static final String Method_Arg_Name = "id";
    private static final String Method_Name = "deleteById";

    @Override
    public void handle(AnnotationValues<DeleteById> annotation, JCAnnotation ast, JavacNode annotationNode) {
        // annotationNode 是上下文
        // 判断是否已经使用了注解
        // handleFlagUsage(annotationNode, ConfigurationKeys.TO_STRING_FLAG_USAGE, "@DeleteById");

        // 如果存在则删除
        deleteAnnotationIfNeccessary(annotationNode, DeleteById.class);

        // 获取该注解，并接下来获取该注解上的属性
        // DeleteById ann = annotation.getInstance();
        String beanClassName = null;
        boolean isRollback = false;
        Iterator<JCExpression> iterator = ast.args.iterator();
        while (iterator.hasNext()) {
            JCAssign next = (JCAssign) iterator.next();
            if (next.lhs.toString().equalsIgnoreCase("value")) {
                String className = next.rhs.toString();
                String beanName = className.substring(0, className.lastIndexOf("."));
                beanClassName = beanName;
            } else if (next.lhs.toString().equalsIgnoreCase("rollback")) {
                isRollback = true;
            }
        }
        // 获取该注解的地方，如果注解在类上面，则获取该类，如果注解在方法上，则获取该方法，如此类推
        JavacNode typeNode = annotationNode.up();
        try {
            generateDeleteById(typeNode, annotationNode, beanClassName, isRollback,false);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 获取该注解，并接下来获取该注解上的属性
        /*java.util.List<Object> extensionProviders = annotation.getActualExpressions("value");
        for (Object extensionProvider : extensionProviders) {
            if (!(extensionProvider instanceof JCFieldAccess)) continue;
            JCFieldAccess provider = (JCFieldAccess) extensionProvider;
            beanClassName = ((JCIdent) provider.selected).sym.toString();
        }*/

    }

    public void generateDeleteById(JavacNode typeNode, JavacNode source, String beanClassName, boolean isRollback, boolean whineIfExists) {
        boolean notAClass = true;
        if (typeNode.get() instanceof JCClassDecl) {
            long flags = ((JCClassDecl)typeNode.get()).mods.flags;
            notAClass = (flags & (Flags.INTERFACE | Flags.ANNOTATION)) != 0;
        }

        if (notAClass) {
            source.addError("@DeleteById is only supported on a class or enum.");
            return;
        }


        boolean isExistsBefore;
        switch (methodExists(Before_Method_Name, typeNode, 1)) {
            case NOT_EXISTS:
                JCMethodDecl method = createDeleteByIdBefore(typeNode, beanClassName, source.get());
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
                JCMethodDecl method = createDeleteByIdAfter(typeNode, beanClassName, source.get());
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
                JCMethodDecl method = createDeleteById(typeNode, beanClassName, isRollback, source.get());
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

    static JCMethodDecl createDeleteById(JavacNode typeNode, String beanClassName, boolean isRollback, JCTree source) {
        JavacTreeMaker maker = typeNode.getTreeMaker();
        ListBuffer bodyBlockList = new ListBuffer();
        JCBlock body;
        Name deleteByIdBeforeName = typeNode.toName(Before_Method_Name);
        Name deleteByIdAfterName = typeNode.toName(After_Method_Name);

        /*
            private boolean deleteByIdBefore(T id) {}
            private void deleteByIdAfter(T id) {}

            @ApiOperation(value = "删除", notes = "删除", httpMethod = "GET")
            @ApiResponse(code = Msg.SUCCESS_CODE, message = "删除成功", response = Msg.class)
            @ApiImplicitParam(value = "序号", required = true, name = "id", paramType = "query")
            @GetMapping("remoteById")
            @Transactional
            public Msg remoteById(@NotNull(message = "Id 不能为空")
                                  @Min(value = 1, message = "Id 不能为空")
                                  Long id) {
                String msg = deleteByIdBefore(id);
                if(msg != null && !"".equals(msg)) {
                    return Msg.ok(msg);
                } else {
                    Ebean.delete(SysOrg.class, id);
                    deleteByIdAfter(id)
                    return Msg.ok("删除成功");
                }
            }
        * */
        ListBuffer methodAnnotations = new ListBuffer();
        // 添加一个 注解
        JCExpression annotationArg =  maker.Literal(Method_Name); // 序列成一个字符串
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

        JCExpression returnRightBodyMsg =  maker.Literal(Module_Discript + "成功");
        JCExpression returnRightBody = chainDots(typeNode, "com", "zyf", "result", "Msg", "ok");
        JCExpression returnRightBodyEnd = maker.Apply(List.<JCExpression>nil(), returnRightBody, List.of(returnRightBodyMsg));
        JCStatement methodReturnBodyEndLine = maker.Return(returnRightBodyEnd);

        Name objName = typeNode.toName(Method_Arg_Name);

        // B
        JCExpression methodParamLeft = chainDotsString(typeNode, "java.lang.Long");

        // 添加一个 注解 @org.springframework.validation.annotation.Validated({com.zyf.valid.DeleteById.class})
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
                List.<JCAnnotation>of(validAnnotation1, validAnnotation2)), objName, methodParamLeft, null);

        // name = deleteByIdBefore

        // deleteByIdBefore
        JCExpression beforeMethodLeft = maker.Ident(deleteByIdBeforeName);
        // obj
        JCExpression beforeMethodArgs = maker.Ident(objName);
        // String
        JCExpression beforeMethodReturnTypeLeft = genTypeRef(typeNode, "java.lang.String");
        // String msg = deleteByIdBefore(obj)
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


        // deleteByIdAfter
        JCExpression afterMethodLeft = maker.Ident(deleteByIdAfterName);
        // obj
        JCExpression afterMethodArgs = maker.Ident(objName);
        // deleteByIdAfter(obj);
        JCStatement afterMethodEnd = maker.Exec(maker.Apply(List.<JCExpression>nil(),
                afterMethodLeft, List.of(afterMethodArgs)));

        // Ebean.delete
        JCExpression objMethod = chainDots(typeNode, "io", "ebean", "Ebean", "deletePermanent");
        // SysOrg.class
        JCExpression beanClass = genTypeRef(typeNode, beanClassName+ ".class");
        // Ebean.delete
        JCExpression objArgs = maker.Ident(objName);
        // Ebean.delete(SysOrg.class, id)
        JCExpression objMethodSuccess = maker.Apply(List.<JCExpression>nil(),
                objMethod, List.<JCExpression>of(beanClass, objArgs));
        // Ebean.delete(SysOrg.class, id);
        JCStatement objStatement = maker.Exec(objMethodSuccess);

        bodyBlockList.append(beforeMethodEnd);
        bodyBlockList.append(ifEnd);
        bodyBlockList.append(objStatement);
        bodyBlockList.append(afterMethodEnd);
        bodyBlockList.append(methodReturnBodyEndLine);

        body = maker.Block(0, bodyBlockList.toList());

        // method
        JCMethodDecl deleteById = maker.MethodDef(methodAccess, typeNode.toName(Method_Name), methodReturnType,
                List.<JCTypeParameter>nil(), List.of(obj), List.<JCExpression>nil(), body, null);
        return recursiveSetGeneratedBy(deleteById, source, typeNode.getContext());
    }


    static JCMethodDecl createDeleteByIdBefore(JavacNode typeNode, String beanClassName, JCTree source) {
        JavacTreeMaker maker = typeNode.getTreeMaker();
        Name deleteByIdBeforeName = typeNode.toName(Before_Method_Name);
        Name objName = typeNode.toName(Method_Arg_Name);
        // Long
        JCExpression keyType = chainDotsString(typeNode, "java.lang.Long");
        // Long id
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
        // private String deleteByIdBefore(Long id) { return ""; }
        JCMethodDecl deleteByIdBeforeMethod = maker.MethodDef(modifiers, deleteByIdBeforeName, beforeType,
                List.<JCTypeParameter>nil(), List.of(obj), List.<JCExpression>nil(), block, null);
        return recursiveSetGeneratedBy(deleteByIdBeforeMethod, source, typeNode.getContext());
    }

    static JCMethodDecl createDeleteByIdAfter(JavacNode typeNode, String beanClassName, JCTree source) {
        JavacTreeMaker maker = typeNode.getTreeMaker();
        Name deleteByIdAfterName = typeNode.toName(After_Method_Name);
        Name objName = typeNode.toName(Method_Arg_Name);

        // Long
        JCExpression keyType = chainDotsString(typeNode, "java.lang.Long");
        // Long id
        JCVariableDecl obj = maker.VarDef(maker.Modifiers(Flags.PARAMETER), objName, keyType, null);
        // private
        JCModifiers modifiers = maker.Modifiers(Flags.PRIVATE);
        // void
        JCExpression afterType = maker.Type(createVoidType(typeNode.getSymbolTable(), CTC_VOID));
        // {}
        JCBlock block = maker.Block(0, List.<JCStatement>nil());
        // protected void deleteByIdAfter(Long id) {}
        JCMethodDecl deleteByIdAfterMethod = maker.MethodDef(modifiers, deleteByIdAfterName, afterType,
                List.<JCTypeParameter>nil(), List.of(obj), List.<JCExpression>nil(), block, null);
        return recursiveSetGeneratedBy(deleteByIdAfterMethod, source, typeNode.getContext());
    }




}
