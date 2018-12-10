package org.whitesource.agent.utils;

import org.whitesource.agent.api.model.DependencyInfo;

import java.util.stream.Stream;

public class AddDependencyFileRecursionHelper {
    private AddDependencyFileRecursionHelper(){}

    public static Stream<DependencyInfo> flatten(DependencyInfo dependencyInfo){
        return Stream.concat(Stream.of(dependencyInfo), dependencyInfo.getChildren().stream().flatMap(AddDependencyFileRecursionHelper::flatten));
    }
}
