/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
$(function() {
    $(':checkbox').change(function(){
        var preferNotToSay = 'prefer not to say';
        var thisDisabilityValue = $(this).attr('value');
        var containerClass = '.disability-container';

        if (thisDisabilityValue.toLowerCase() === preferNotToSay) {
            if (this.checked) {
                // Here we need to uncheck all the other checkboxes
                $(this).closest(containerClass).siblings()
                    .find(':checkbox').attr('checked', false);
                // We also need to hide the text area for the other disability category
                // The id is in the target attribute in the parent label
                var target = $(this).parent().attr('target');
                $('#' + target).hide().attr('aria-hidden', true);
            }
        } else {
            if (this.checked) {
                // Here we need to uncheck the prefer not to say checkbox
                var checkboxes = $(this).closest(containerClass).siblings().find(':checkbox');
                checkboxes.each(function () {
                    var disabilityValue = $(this).attr('value');
                    if (disabilityValue.toLowerCase() === preferNotToSay) {
                        $(this).attr('checked', false)
                    }
                });
            }
        }
    });
});
