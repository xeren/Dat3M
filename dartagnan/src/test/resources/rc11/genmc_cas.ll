; ModuleID = '/home/jonas/BA/Dat3M/./benchmarks/rc11/genmc_cas.c'
source_filename = "/home/jonas/BA/Dat3M/./benchmarks/rc11/genmc_cas.c"
target datalayout = "e-m:e-p270:32:32-p271:32:32-p272:64:64-i64:64-i128:128-f80:128-n8:16:32:64-S128"
target triple = "x86_64-pc-linux-gnu"

@r = dso_local global i32 0, align 4, !dbg !0
@rw1 = dso_local global i32 1, align 4, !dbg !7
@rw2 = dso_local global i32 2, align 4, !dbg !14
@cas1 = dso_local global i32 0, align 4, !dbg !33
@cas2 = dso_local global i32 0, align 4, !dbg !35
@.str = private unnamed_addr constant [7 x i8] c"r != 1\00", align 1, !dbg !16
@.str.1 = private unnamed_addr constant [51 x i8] c"/home/jonas/BA/Dat3M/./benchmarks/rc11/genmc_cas.c\00", align 1, !dbg !22
@__PRETTY_FUNCTION__.main = private unnamed_addr constant [11 x i8] c"int main()\00", align 1, !dbg !27

; Function Attrs: noinline nounwind uwtable
define dso_local ptr @worker1(ptr noundef %0) #0 !dbg !45 {
  %2 = alloca ptr, align 8
  %3 = alloca i32, align 4
  %4 = alloca i32, align 4
  %5 = alloca i32, align 4
  %6 = alloca i8, align 1
  store ptr %0, ptr %2, align 8
  call void @llvm.dbg.declare(metadata ptr %2, metadata !49, metadata !DIExpression()), !dbg !50
  call void @llvm.dbg.declare(metadata ptr %3, metadata !51, metadata !DIExpression()), !dbg !52
  %7 = load atomic i32, ptr @r seq_cst, align 4, !dbg !53
  store i32 %7, ptr %4, align 4, !dbg !53
  %8 = load i32, ptr %4, align 4, !dbg !53
  store i32 %8, ptr %3, align 4, !dbg !52
  %9 = load atomic i32, ptr @rw1 seq_cst, align 4, !dbg !54
  store i32 %9, ptr %5, align 4, !dbg !54
  %10 = load i32, ptr %3, align 4, !dbg !54
  %11 = load i32, ptr %5, align 4, !dbg !54
  %12 = cmpxchg ptr @r, i32 %10, i32 %11 seq_cst seq_cst, align 4, !dbg !54
  %13 = extractvalue { i32, i1 } %12, 0, !dbg !54
  %14 = extractvalue { i32, i1 } %12, 1, !dbg !54
  br i1 %14, label %16, label %15, !dbg !54

15:                                               ; preds = %1
  store i32 %13, ptr %3, align 4, !dbg !54
  br label %16, !dbg !54

16:                                               ; preds = %15, %1
  %17 = zext i1 %14 to i8, !dbg !54
  store i8 %17, ptr %6, align 1, !dbg !54
  %18 = load i8, ptr %6, align 1, !dbg !54
  %19 = trunc i8 %18 to i1, !dbg !54
  %20 = zext i1 %19 to i32, !dbg !54
  store i32 %20, ptr @cas1, align 4, !dbg !55
  ret ptr null, !dbg !56
}

; Function Attrs: nocallback nofree nosync nounwind speculatable willreturn memory(none)
declare void @llvm.dbg.declare(metadata, metadata, metadata) #1

; Function Attrs: noinline nounwind uwtable
define dso_local ptr @worker2(ptr noundef %0) #0 !dbg !57 {
  %2 = alloca ptr, align 8
  %3 = alloca i32, align 4
  %4 = alloca i32, align 4
  %5 = alloca i32, align 4
  %6 = alloca i8, align 1
  store ptr %0, ptr %2, align 8
  call void @llvm.dbg.declare(metadata ptr %2, metadata !58, metadata !DIExpression()), !dbg !59
  call void @llvm.dbg.declare(metadata ptr %3, metadata !60, metadata !DIExpression()), !dbg !61
  %7 = load atomic i32, ptr @r seq_cst, align 4, !dbg !62
  store i32 %7, ptr %4, align 4, !dbg !62
  %8 = load i32, ptr %4, align 4, !dbg !62
  store i32 %8, ptr %3, align 4, !dbg !61
  %9 = load atomic i32, ptr @rw2 seq_cst, align 4, !dbg !63
  store i32 %9, ptr %5, align 4, !dbg !63
  %10 = load i32, ptr %3, align 4, !dbg !63
  %11 = load i32, ptr %5, align 4, !dbg !63
  %12 = cmpxchg ptr @r, i32 %10, i32 %11 seq_cst seq_cst, align 4, !dbg !63
  %13 = extractvalue { i32, i1 } %12, 0, !dbg !63
  %14 = extractvalue { i32, i1 } %12, 1, !dbg !63
  br i1 %14, label %16, label %15, !dbg !63

15:                                               ; preds = %1
  store i32 %13, ptr %3, align 4, !dbg !63
  br label %16, !dbg !63

16:                                               ; preds = %15, %1
  %17 = zext i1 %14 to i8, !dbg !63
  store i8 %17, ptr %6, align 1, !dbg !63
  %18 = load i8, ptr %6, align 1, !dbg !63
  %19 = trunc i8 %18 to i1, !dbg !63
  %20 = zext i1 %19 to i32, !dbg !63
  store i32 %20, ptr @cas2, align 4, !dbg !64
  ret ptr null, !dbg !65
}

; Function Attrs: noinline nounwind uwtable
define dso_local i32 @main() #0 !dbg !66 {
  %1 = alloca i32, align 4
  %2 = alloca [2 x i64], align 16
  store i32 0, ptr %1, align 4
  call void @llvm.dbg.declare(metadata ptr %2, metadata !69, metadata !DIExpression()), !dbg !76
  %3 = getelementptr inbounds [2 x i64], ptr %2, i64 0, i64 0, !dbg !77
  %4 = call i32 @pthread_create(ptr noundef %3, ptr noundef null, ptr noundef @worker1, ptr noundef null) #5, !dbg !78
  %5 = getelementptr inbounds [2 x i64], ptr %2, i64 0, i64 1, !dbg !79
  %6 = call i32 @pthread_create(ptr noundef %5, ptr noundef null, ptr noundef @worker2, ptr noundef null) #5, !dbg !80
  %7 = getelementptr inbounds [2 x i64], ptr %2, i64 0, i64 0, !dbg !81
  %8 = load i64, ptr %7, align 16, !dbg !81
  %9 = call i32 @pthread_join(i64 noundef %8, ptr noundef null), !dbg !82
  %10 = getelementptr inbounds [2 x i64], ptr %2, i64 0, i64 1, !dbg !83
  %11 = load i64, ptr %10, align 8, !dbg !83
  %12 = call i32 @pthread_join(i64 noundef %11, ptr noundef null), !dbg !84
  %13 = load i32, ptr @cas1, align 4, !dbg !85
  %14 = icmp ne i32 %13, 0, !dbg !85
  br i1 %14, label %15, label %24, !dbg !87

15:                                               ; preds = %0
  %16 = load i32, ptr @cas2, align 4, !dbg !88
  %17 = icmp ne i32 %16, 0, !dbg !88
  br i1 %17, label %18, label %24, !dbg !89

18:                                               ; preds = %15
  %19 = load atomic i32, ptr @r seq_cst, align 4, !dbg !90
  %20 = icmp ne i32 %19, 1, !dbg !90
  br i1 %20, label %21, label %22, !dbg !94

21:                                               ; preds = %18
  br label %23, !dbg !94

22:                                               ; preds = %18
  call void @__assert_fail(ptr noundef @.str, ptr noundef @.str.1, i32 noundef 40, ptr noundef @__PRETTY_FUNCTION__.main) #6, !dbg !90
  unreachable, !dbg !90

23:                                               ; preds = %21
  br label %24, !dbg !95

24:                                               ; preds = %23, %15, %0
  ret i32 0, !dbg !96
}

; Function Attrs: nounwind
declare i32 @pthread_create(ptr noundef, ptr noundef, ptr noundef, ptr noundef) #2

declare i32 @pthread_join(i64 noundef, ptr noundef) #3

; Function Attrs: noreturn nounwind
declare void @__assert_fail(ptr noundef, ptr noundef, i32 noundef, ptr noundef) #4

attributes #0 = { noinline nounwind uwtable "frame-pointer"="all" "min-legal-vector-width"="0" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="x86-64" "target-features"="+cmov,+cx8,+fxsr,+mmx,+sse,+sse2,+x87" "tune-cpu"="generic" }
attributes #1 = { nocallback nofree nosync nounwind speculatable willreturn memory(none) }
attributes #2 = { nounwind "frame-pointer"="all" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="x86-64" "target-features"="+cmov,+cx8,+fxsr,+mmx,+sse,+sse2,+x87" "tune-cpu"="generic" }
attributes #3 = { "frame-pointer"="all" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="x86-64" "target-features"="+cmov,+cx8,+fxsr,+mmx,+sse,+sse2,+x87" "tune-cpu"="generic" }
attributes #4 = { noreturn nounwind "frame-pointer"="all" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="x86-64" "target-features"="+cmov,+cx8,+fxsr,+mmx,+sse,+sse2,+x87" "tune-cpu"="generic" }
attributes #5 = { nounwind }
attributes #6 = { noreturn nounwind }

!llvm.dbg.cu = !{!2}
!llvm.module.flags = !{!37, !38, !39, !40, !41, !42, !43}
!llvm.ident = !{!44}

!0 = !DIGlobalVariableExpression(var: !1, expr: !DIExpression())
!1 = distinct !DIGlobalVariable(name: "r", scope: !2, file: !9, line: 6, type: !10, isLocal: false, isDefinition: true)
!2 = distinct !DICompileUnit(language: DW_LANG_C11, file: !3, producer: "Ubuntu clang version 18.1.3 (1ubuntu1)", isOptimized: false, runtimeVersion: 0, emissionKind: FullDebug, retainedTypes: !4, globals: !6, splitDebugInlining: false, nameTableKind: None)
!3 = !DIFile(filename: "/home/jonas/BA/Dat3M/./benchmarks/rc11/genmc_cas.c", directory: "/home/jonas/BA/Dat3M", checksumkind: CSK_MD5, checksum: "d695700cdc3457173a3fcf8fb92c1044")
!4 = !{!5}
!5 = !DIDerivedType(tag: DW_TAG_pointer_type, baseType: null, size: 64)
!6 = !{!0, !7, !14, !16, !22, !27, !33, !35}
!7 = !DIGlobalVariableExpression(var: !8, expr: !DIExpression())
!8 = distinct !DIGlobalVariable(name: "rw1", scope: !2, file: !9, line: 7, type: !10, isLocal: false, isDefinition: true)
!9 = !DIFile(filename: "./benchmarks/rc11/genmc_cas.c", directory: "/home/jonas/BA/Dat3M", checksumkind: CSK_MD5, checksum: "d695700cdc3457173a3fcf8fb92c1044")
!10 = !DIDerivedType(tag: DW_TAG_typedef, name: "atomic_int", file: !11, line: 101, baseType: !12)
!11 = !DIFile(filename: "/usr/lib/llvm-18/lib/clang/18/include/stdatomic.h", directory: "", checksumkind: CSK_MD5, checksum: "1c2aee2a953dfc5407753d1f439b6977")
!12 = !DIDerivedType(tag: DW_TAG_atomic_type, baseType: !13)
!13 = !DIBasicType(name: "int", size: 32, encoding: DW_ATE_signed)
!14 = !DIGlobalVariableExpression(var: !15, expr: !DIExpression())
!15 = distinct !DIGlobalVariable(name: "rw2", scope: !2, file: !9, line: 8, type: !10, isLocal: false, isDefinition: true)
!16 = !DIGlobalVariableExpression(var: !17, expr: !DIExpression())
!17 = distinct !DIGlobalVariable(scope: null, file: !9, line: 40, type: !18, isLocal: true, isDefinition: true)
!18 = !DICompositeType(tag: DW_TAG_array_type, baseType: !19, size: 56, elements: !20)
!19 = !DIBasicType(name: "char", size: 8, encoding: DW_ATE_signed_char)
!20 = !{!21}
!21 = !DISubrange(count: 7)
!22 = !DIGlobalVariableExpression(var: !23, expr: !DIExpression())
!23 = distinct !DIGlobalVariable(scope: null, file: !9, line: 40, type: !24, isLocal: true, isDefinition: true)
!24 = !DICompositeType(tag: DW_TAG_array_type, baseType: !19, size: 408, elements: !25)
!25 = !{!26}
!26 = !DISubrange(count: 51)
!27 = !DIGlobalVariableExpression(var: !28, expr: !DIExpression())
!28 = distinct !DIGlobalVariable(scope: null, file: !9, line: 40, type: !29, isLocal: true, isDefinition: true)
!29 = !DICompositeType(tag: DW_TAG_array_type, baseType: !30, size: 88, elements: !31)
!30 = !DIDerivedType(tag: DW_TAG_const_type, baseType: !19)
!31 = !{!32}
!32 = !DISubrange(count: 11)
!33 = !DIGlobalVariableExpression(var: !34, expr: !DIExpression())
!34 = distinct !DIGlobalVariable(name: "cas1", scope: !2, file: !9, line: 10, type: !13, isLocal: false, isDefinition: true)
!35 = !DIGlobalVariableExpression(var: !36, expr: !DIExpression())
!36 = distinct !DIGlobalVariable(name: "cas2", scope: !2, file: !9, line: 11, type: !13, isLocal: false, isDefinition: true)
!37 = !{i32 7, !"Dwarf Version", i32 5}
!38 = !{i32 2, !"Debug Info Version", i32 3}
!39 = !{i32 1, !"wchar_size", i32 4}
!40 = !{i32 8, !"PIC Level", i32 2}
!41 = !{i32 7, !"PIE Level", i32 2}
!42 = !{i32 7, !"uwtable", i32 2}
!43 = !{i32 7, !"frame-pointer", i32 2}
!44 = !{!"Ubuntu clang version 18.1.3 (1ubuntu1)"}
!45 = distinct !DISubprogram(name: "worker1", scope: !9, file: !9, line: 13, type: !46, scopeLine: 14, flags: DIFlagPrototyped, spFlags: DISPFlagDefinition, unit: !2, retainedNodes: !48)
!46 = !DISubroutineType(types: !47)
!47 = !{!5, !5}
!48 = !{}
!49 = !DILocalVariable(name: "arg", arg: 1, scope: !45, file: !9, line: 13, type: !5)
!50 = !DILocation(line: 13, column: 21, scope: !45)
!51 = !DILocalVariable(name: "r2", scope: !45, file: !9, line: 15, type: !13)
!52 = !DILocation(line: 15, column: 9, scope: !45)
!53 = !DILocation(line: 15, column: 14, scope: !45)
!54 = !DILocation(line: 16, column: 12, scope: !45)
!55 = !DILocation(line: 16, column: 10, scope: !45)
!56 = !DILocation(line: 18, column: 5, scope: !45)
!57 = distinct !DISubprogram(name: "worker2", scope: !9, file: !9, line: 21, type: !46, scopeLine: 22, flags: DIFlagPrototyped, spFlags: DISPFlagDefinition, unit: !2, retainedNodes: !48)
!58 = !DILocalVariable(name: "arg", arg: 1, scope: !57, file: !9, line: 21, type: !5)
!59 = !DILocation(line: 21, column: 21, scope: !57)
!60 = !DILocalVariable(name: "r2", scope: !57, file: !9, line: 23, type: !13)
!61 = !DILocation(line: 23, column: 9, scope: !57)
!62 = !DILocation(line: 23, column: 14, scope: !57)
!63 = !DILocation(line: 24, column: 12, scope: !57)
!64 = !DILocation(line: 24, column: 10, scope: !57)
!65 = !DILocation(line: 26, column: 5, scope: !57)
!66 = distinct !DISubprogram(name: "main", scope: !9, file: !9, line: 29, type: !67, scopeLine: 30, spFlags: DISPFlagDefinition, unit: !2, retainedNodes: !48)
!67 = !DISubroutineType(types: !68)
!68 = !{!13}
!69 = !DILocalVariable(name: "t", scope: !66, file: !9, line: 31, type: !70)
!70 = !DICompositeType(tag: DW_TAG_array_type, baseType: !71, size: 128, elements: !74)
!71 = !DIDerivedType(tag: DW_TAG_typedef, name: "pthread_t", file: !72, line: 27, baseType: !73)
!72 = !DIFile(filename: "/usr/include/x86_64-linux-gnu/bits/pthreadtypes.h", directory: "", checksumkind: CSK_MD5, checksum: "8a5acdbeec491eca11cf81cb1ef77ea7")
!73 = !DIBasicType(name: "unsigned long", size: 64, encoding: DW_ATE_unsigned)
!74 = !{!75}
!75 = !DISubrange(count: 2)
!76 = !DILocation(line: 31, column: 15, scope: !66)
!77 = !DILocation(line: 33, column: 21, scope: !66)
!78 = !DILocation(line: 33, column: 5, scope: !66)
!79 = !DILocation(line: 34, column: 21, scope: !66)
!80 = !DILocation(line: 34, column: 5, scope: !66)
!81 = !DILocation(line: 36, column: 18, scope: !66)
!82 = !DILocation(line: 36, column: 5, scope: !66)
!83 = !DILocation(line: 37, column: 18, scope: !66)
!84 = !DILocation(line: 37, column: 5, scope: !66)
!85 = !DILocation(line: 39, column: 9, scope: !86)
!86 = distinct !DILexicalBlock(scope: !66, file: !9, line: 39, column: 9)
!87 = !DILocation(line: 39, column: 14, scope: !86)
!88 = !DILocation(line: 39, column: 17, scope: !86)
!89 = !DILocation(line: 39, column: 9, scope: !66)
!90 = !DILocation(line: 40, column: 9, scope: !91)
!91 = distinct !DILexicalBlock(scope: !92, file: !9, line: 40, column: 9)
!92 = distinct !DILexicalBlock(scope: !93, file: !9, line: 40, column: 9)
!93 = distinct !DILexicalBlock(scope: !86, file: !9, line: 39, column: 23)
!94 = !DILocation(line: 40, column: 9, scope: !92)
!95 = !DILocation(line: 41, column: 5, scope: !93)
!96 = !DILocation(line: 43, column: 5, scope: !66)
