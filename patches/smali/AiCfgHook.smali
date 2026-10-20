.class public Lcom/scholaread/aicfg/AiCfgHook;
.super Ljava/lang/Object;
.source "AiCfgHook.java"

.implements Landroid/view/View$OnClickListener;

.field private static sCtx:Landroid/content/Context;

.method public constructor <init>()V
    .registers 1

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static attach(Ljava/lang/Object;)V
    .registers 8

    instance-of v0, p0, Lcom/scholaread/b/hj;

    if-eqz v0, :cond_aicfg_bail_type

    check-cast p0, Lcom/scholaread/b/hj;

    iget-object v0, p0, Lcom/scholaread/b/hj;->o:Lcom/google/android/flexbox/FlexboxLayout;

    if-eqz v0, :cond_aicfg_bail_o

    iget-object v1, p0, Lcom/scholaread/b/hj;->K:Landroid/widget/TextView;

    if-eqz v1, :cond_aicfg_bail_k

    invoke-virtual {v0}, Landroid/view/View;->getParent()Landroid/view/ViewParent;

    move-result-object v2

    instance-of v3, v2, Landroid/view/ViewGroup;

    if-eqz v3, :cond_aicfg_bail_parent

    invoke-virtual {v0}, Landroid/view/View;->getContext()Landroid/content/Context;

    move-result-object v3

    new-instance v4, Landroid/widget/TextView;

    invoke-direct {v4, v3}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V

    const-string v5, "\u81ea\u5b9a\u4e49 AI \u63a5\u53e3\uff08\u672c\u5730 / \u81ea\u5b9a\u4e49 API\uff09"

    invoke-virtual {v4, v5}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    invoke-virtual {v1}, Landroid/widget/TextView;->getCurrentTextColor()I

    move-result v5

    invoke-virtual {v4, v5}, Landroid/widget/TextView;->setTextColor(I)V

    invoke-virtual {v1}, Landroid/widget/TextView;->getTextSize()F

    move-result v5

    const/4 v6, 0

    invoke-virtual {v4, v6, v5}, Landroid/widget/TextView;->setTextSize(IF)V

    const/16 v5, 0x10

    invoke-virtual {v4, v5}, Landroid/widget/TextView;->setGravity(I)V

    invoke-virtual {v0}, Landroid/view/View;->getPaddingLeft()I

    move-result v5

    invoke-virtual {v3}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v6

    invoke-virtual {v6}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;

    move-result-object v6

    iget v6, v6, Landroid/util/DisplayMetrics;->density:F

    const/high16 v7, 0x41700000    # float 15.0f

    mul-float/2addr v6, v7

    float-to-int v6, v6

    invoke-virtual {v0}, Landroid/view/View;->getPaddingRight()I

    move-result v7

    invoke-virtual {v4, v5, v6, v7, v6}, Landroid/view/View;->setPadding(IIII)V

    invoke-virtual {v0}, Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup$LayoutParams;

    move-result-object v5

    invoke-virtual {v4, v5}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    new-instance v5, Lcom/scholaread/aicfg/AiCfgHook;

    invoke-direct {v5}, Lcom/scholaread/aicfg/AiCfgHook;-><init>()V

    invoke-virtual {v4, v5}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    invoke-virtual {v2, v0}, Landroid/view/ViewGroup;->indexOfChild(Landroid/view/View;)I

    move-result v6

    add-int/lit8 v6, v6, 1

    invoke-virtual {v2, v4, v6}, Landroid/view/ViewGroup;->addView(Landroid/view/View;I)V

    const-string v4, "AiCfgHook"

    const-string v5, "row added"

    invoke-static {v4, v5}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    sput-object v3, Lcom/scholaread/aicfg/AiCfgHook;->sCtx:Landroid/content/Context;

    :cond_aicfg_end
    return-void

    :cond_aicfg_bail_type
    const-string v4, "AiCfgHook"

    const-string v5, "bail: not hj binding"

    invoke-static {v4, v5}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    return-void

    :cond_aicfg_bail_o
    const-string v4, "AiCfgHook"

    const-string v5, "bail: panel null"

    invoke-static {v4, v5}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    return-void

    :cond_aicfg_bail_k
    const-string v4, "AiCfgHook"

    const-string v5, "bail: title null"

    invoke-static {v4, v5}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    return-void

    :cond_aicfg_bail_parent
    const-string v4, "AiCfgHook"

    const-string v5, "bail: parent not ViewGroup"

    invoke-static {v4, v5}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    return-void
.end method

.method public onClick(Landroid/view/View;)V
    .registers 2

    sget-object v0, Lcom/scholaread/aicfg/AiCfgHook;->sCtx:Landroid/content/Context;

    if-eqz v0, :cond_aicfg_oc_end

    invoke-static {v0}, Lcom/scholaread/aicfg/AiCfgPanel;->show(Landroid/content/Context;)V

    :cond_aicfg_oc_end
    return-void
.end method
