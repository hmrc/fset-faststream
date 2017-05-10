$(function () {
  "use strict";

  var ENTER_KEY = 13;
  var BAD_REQUEST = 400;
  var NOT_FOUND = 404;
  var addressLookupUrlBase = "/fset-fast-stream/address-search/";

  $(document).ready(function () {
    $(window).keydown(function (event) {
      if (event.keyCode === 13) {
        event.preventDefault();
        return false;
      }
    });
  });

  jQuery.fn.scrollTo = function (elem, speed) {
    $(this).animate({
      scrollTop: $(this).scrollTop() - $(this).offset().top + $(elem).offset().top
    }, speed === undefined ? 1000 : speed);
    return this;
  };

  $("#findAddressBtn").on("click", function (event) {
    event.preventDefault();
    $("#addressSelectorList").empty();
    $("#invalidPostcodeWrapper").slideUp(300);
    $('#addressesNotFoundWrapper').slideUp(300);
    addressSearchByPostcode($("#post-code-search").val());
    return false;
  });

  function sanitisePostcode(postcode) {
    return postcode.toUpperCase().replace(/ /g, '');
  }

  function addressSearchByPostcode(postcode) {
    var url = addressLookupUrlBase + "?postcode=" + sanitisePostcode(postcode);
    $.getJSON(url, function (result) {
      $.map(result, function (lookupReply) {
        var linkText = lookupReply.address.lines[0] + " " + lookupReply.address.town;
        $("#addressSelectorList").append(
          '<li><a href="#" addressId="' + lookupReply.id + '">' + linkText + '</a></li>'
        );
      });
      var addressSelector = $("#addressSelectorContainer");

      addressSelector.removeClass('toggle-content');
      addressSelector.slideDown("slow");
      showResultsLink(result.length);

    }).fail(function (xhr, textStatus, error) {
      if (xhr.status === BAD_REQUEST) {
        showInvalidPostcodePanel();
      } else if (xhr.status === NOT_FOUND) {
        showAddressesNotFoundPanel();
      }
    });
  }

  function showResultsLink(num) {
    var link = $("#addressesFound");
    if (num === 1) {
      link.text(num + " result found");
    } else {
      link.text(num + " results found");
    }

    link.removeClass("hidden");
  }

  function getAddressById(id, successFunction) {
    $.getJSON(addressLookupUrlBase + id,  successFunction).fail(function (xhr, textStatus, error) {
      // Should never get here (TM)
      // as we should always get the ID from a previous address search
      if (xhr.status === BAD_REQUEST) {
        showInvalidPostcodePanel();
      } else if (xhr.status === NOT_FOUND) {
        showAddressesNotFoundPanel();
      }
    });
  }

  function showAddressesNotFoundPanel() {
    $('#addressesNotFoundWrapper').slideDown(300);
  }

  function showInvalidPostcodePanel() {
    $('#invalidPostcodeWrapper').slideDown(300);
  }

  function populateAddressFields(addressRecord) {
    var addressInput = $("#addressManualInput");
    addressInput.removeClass("disabled");

    var addressLines = addressRecord.address.lines.slice(0, 3);
    addressLines.push(addressRecord.address.town);

    // the escaping here is because of the way we render the Ids from the Play field constructor.
    // In order for the 'jump to error' links on the page to work there is a dot in the Id, which is
    // interpreted as a class selector here.
    $("#address\\.line1").val(addressLines[0]);

    $("#address_line2").val(addressLines[1] || "");
    $("#address_line3").val(addressLines[2] || "");
    $("#address_line4").val(addressLines[3] || "");
    $("#postCode").val(addressRecord.address.postcode);

    $("html, body").animate({
      scrollTop: addressInput.offset().top
    }, 400);

  }

  $("#addressSelectorList").on("click", "a", function (event) {
    event.preventDefault();
    $("#outsideUk:checked").trigger("click");
    getAddressById(this.getAttribute("addressId"), populateAddressFields);

  });
});
