$(function()
{
    /*
      By Osvaldas Valutis, www.osvaldas.info
      Available for use under the MIT License
    */
    'use strict';

    (function($, window, document, undefined)
    {
        $('.input-file').each(function()
        {
            var $input = $(this),
                $label = $input.next('label'),
                labelVal = $label.html();
            $input.on('change', function(e)
            {
                var fileName = '';
                if (this.files && this.files.length > 1)
                    fileName = (this.getAttribute(
                        'data-multiple-caption') || '').replace(
                        '{count}', this.files.length);
                else if (e.target.value)
                    fileName = e.target.value.split('\\').pop();
                if (fileName)
                {
                    $('#fileSelected').addClass('panel-indent').html(
                        '<h3 class="bold-small">Document you\'re submitting:</h3><p><i class="fa fa-paperclip" aria-hidden="true"></i>' +
                        fileName + '</p>');
                    $('#importScoresBtn').show();
                    $label.removeClass('button').addClass(
                        'button-link').text(
                        'Upload different document');
                    $('#downloadBtn').hide();
                }
                else
                {
                    $label.html(labelVal);
                    $('#importScoresBtn').hide();
                }
            });
            // Firefox bug fix
            $input
                .on('focus', function()
                {
                    $input.addClass('has-focus');
                })
                .on('blur', function()
                {
                    $input.removeClass('has-focus');
                });
        });
    })(jQuery, window, document);
});
