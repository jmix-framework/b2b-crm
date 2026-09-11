package com.company.crm.test.util;

import com.vaadin.flow.component.AbstractField.ComponentValueChangeEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.textfield.TextField;
import io.jmix.flowui.model.BaseCollectionLoader;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static com.company.crm.app.util.ui.datacontext.DataContextUtils.applyFiltersOnValueChange;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers the page reset that {@code applyFiltersOnValueChange} adds to a filter field: a filter changed by
 * the user must show the first page, because pagination can leave the loader at an offset that is past the
 * end of the new result set (issue #35).
 */
class DataContextUtilsTest {

    private final BaseCollectionLoader dataLoader = mock(BaseCollectionLoader.class);
    private final AtomicInteger appliedFilters = new AtomicInteger();
    private final TextField searchField = new TextField();

    @Test
    void userChangeResetsTheLoaderToTheFirstPage() {
        applyFiltersOnValueChange(dataLoader, appliedFilters::incrementAndGet, searchField);

        fireValueChangeFromClient(searchField);

        verify(dataLoader).setFirstResult(0);
        assertThat(appliedFilters).hasValue(1);
    }

    @Test
    void programmaticChangeKeepsTheCurrentPage() {
        applyFiltersOnValueChange(dataLoader, appliedFilters::incrementAndGet, searchField);

        // this is how a value restored from the URL reaches a filter field
        searchField.setValue("Client name");

        verify(dataLoader, never()).setFirstResult(0);
        assertThat(appliedFilters).hasValue(1);
    }

    @Test
    void everyGivenFieldIsSubscribed() {
        TextField secondField = new TextField();
        applyFiltersOnValueChange(dataLoader, appliedFilters::incrementAndGet, searchField, secondField);

        fireValueChangeFromClient(searchField);
        fireValueChangeFromClient(secondField);

        assertThat(appliedFilters).hasValue(2);
    }

    private static void fireValueChangeFromClient(TextField field) {
        ComponentUtil.fireEvent(field,
                new ComponentValueChangeEvent<>(field, field, field.getEmptyValue(), true));
    }
}
