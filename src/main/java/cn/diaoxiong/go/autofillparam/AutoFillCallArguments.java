package cn.diaoxiong.go.autofillparam;

import com.goide.GoLanguage;
import com.goide.psi.*;
import com.goide.psi.impl.GoLightType;
import com.intellij.codeInsight.hint.ShowParameterInfoContext;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.parameterInfo.LanguageParameterInfo;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Jimmy.li
 * @date 2023/12/13 21:28
 */
public class AutoFillCallArguments extends PsiElementBaseIntentionAction implements IntentionAction {

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        if (editor == null) {
            return;
        }

        if (!ApplicationManager.getApplication().isDispatchThread()) {
            return;
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments();

        List<GoParamDefinition> goParamDefinitionList = getGoParamDefinitions(project, editor);

        insertParameters(editor, goParamDefinitionList);
    }

    private void insertParameters(final Editor editor, final List<GoParamDefinition> goParamDefinitionList) {
        final var doc = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();
        final var textUnderCaret = doc.getText(new TextRange(offset, offset + 1));
        final var textLeftOfCaret = doc.getText(new TextRange(offset - 1, offset));
        if (textLeftOfCaret.startsWith("(")) {
            // do nothing
        } else if (textUnderCaret.startsWith("(")) {
            offset++;
        } else if (!textUnderCaret.startsWith(")")) {
            offset--;
        }
        StringJoiner stringJoiner = new StringJoiner(", ");
        for (GoParamDefinition goParamDefinition : goParamDefinitionList) {
            stringJoiner.add(goParamDefinition.getName());
        }
        String insertString = stringJoiner.toString();
        doc.insertString(offset, insertString);
        editor.getCaretModel().moveToOffset(offset + insertString.length() + 1);
    }

    @NotNull
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<GoParamDefinition> getGoParamDefinitions(@NotNull Project project, Editor editor) {
        final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);

        final int offset = editor.getCaretModel().getOffset();

        final ShowParameterInfoContext context = new ShowParameterInfoContext(editor, project, file, offset, -1, false, false);

        final var dumbService = DumbService.getInstance(project);
        List<ParameterInfoHandler> parameterInfoHandlers = dumbService.filterByDumbAwareness(LanguageParameterInfo.INSTANCE.allForLanguage(GoLanguage.INSTANCE));

        List<Object> collect = parameterInfoHandlers.stream()
                .map(handler -> handler.findElementForParameterInfo(context))
                .filter(Objects::nonNull)
                .flatMap(e -> Arrays.stream(context.getItemsToShow()))
                .filter(GoLightType.class::isInstance)
                .collect(Collectors.toList());

        if (collect.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            GoLightType<GoSignatureOwner> goLightType = (GoLightType<GoSignatureOwner>) collect.get(0);
            GoSignature goSignature = (GoSignature) goLightType.getRealChildren().get(0);
            GoParameters goParameters = (GoParameters) goSignature.getChildren()[0];

            return goParameters.getDefinitionList();
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        final GoCallExpr goCallExpr = findParent(GoCallExpr.class, element);
        return goCallExpr != null;
    }

    @Nullable
    private <T> T findParent(final Class<T> aClass, final PsiElement element) {
        if (element == null || aClass == null) {
            return null;
        } else if (aClass.isAssignableFrom(element.getClass())) {
            return aClass.cast(element);
        } else {
            return findParent(aClass, element.getParent());
        }
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return getText();
    }

    @Override
    public @IntentionName @NotNull String getText() {
        return "Auto fill call parameters";
    }
}
