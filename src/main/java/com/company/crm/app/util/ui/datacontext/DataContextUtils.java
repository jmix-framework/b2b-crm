package com.company.crm.app.util.ui.datacontext;

import com.vaadin.flow.component.HasValue;
import io.jmix.core.Sort;
import io.jmix.core.querycondition.Condition;
import io.jmix.core.querycondition.LogicalCondition;
import io.jmix.core.repository.JmixDataRepositoryContext;
import io.jmix.flowui.model.BaseCollectionLoader;

public final class DataContextUtils {

    public static JmixDataRepositoryContext addCondition(JmixDataRepositoryContext context, Condition condition) {
        Condition resultCondition;
        if (context.condition() != null) {
            resultCondition = LogicalCondition.and(context.condition(), condition);
        } else {
            resultCondition = condition;
        }
        return new JmixDataRepositoryContext(context.fetchPlan(), resultCondition, context.hints());
    }

    public static void installSortByCreatedDate(BaseCollectionLoader dataLoader) {
        dataLoader.setSort(Sort.by(Sort.Direction.DESC, "createdDate"));
    }

    /**
     * Runs {@code applyFilters} whenever one of the given filter {@code fields} changes its value.
     * <p>
     * A change made by the user also resets the loader to the first page, see
     * {@link #resetToFirstPage(BaseCollectionLoader)}. A programmatic change keeps the current page:
     * filter fields are also populated when the state is restored from the URL, and resetting there
     * would drop the page restored from the same URL by the pagination binder.
     */
    public static void applyFiltersOnValueChange(BaseCollectionLoader dataLoader,
                                                 Runnable applyFilters,
                                                 HasValue<?, ?>... fields) {
        for (HasValue<?, ?> field : fields) {
            field.addValueChangeListener(event -> {
                if (event.isFromClient()) {
                    resetToFirstPage(dataLoader);
                }
                applyFilters.run();
            });
        }
    }

    public static void resetToFirstPage(BaseCollectionLoader dataLoader) {
        dataLoader.setFirstResult(0);
    }

    private DataContextUtils() {
    }
}
