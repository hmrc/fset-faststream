$(function () {
  "use strict";

  const ENTER_KEY = 13;
  const BAD_REQUEST = 400;
  const NOT_FOUND = 404;
  const addressLookupUrlBase = "/fset-fast-stream/address-search/";

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
    $("#invalidPostcodeWrapper").slideUp(300);
    addressSearchByPostcode($("#post-code-search").val());
    return false;
  });

  function sanitisePostcode(postcode) {
    return postcode.toUpperCase().replace(/ /g, '');
  }

  function addressSearchByPostcode(postcode) {
    let url = addressLookupUrlBase + "?postcode=" + sanitisePostcode(postcode);
    $.getJSON(url, function (result) {
      console.log(result);
      $.map(result, function (lookupReply) {
        let linkText = lookupReply.address.lines[0] + " " + lookupReply.address.town;

        $("#addressSelectorList").append(
          '<li><a href="#" addressId="' + lookupReply.id + '">' + linkText + '</a></li>'
        );
      });
      let addressSelector = $("#addressSelectorContainer");

      addressSelector.removeClass('toggle-content');
      addressSelector.slideDown("slow");

    }).fail(function (xhr, textStatus, error) {
      if (xhr.status === BAD_REQUEST) {
        showInvalidPostcodePanel();
      } else if (xhr.status === NOT_FOUND) {
        showAddressesNotFoundPanel();
      }
    });
  }


  function getAddressById(id) {
    $.getJSON(addressLookupUrlBase + id, function (result) {

    }).fail(function (xhr, textStatus, error) {
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

  $("#addressSelectorList").on("click", "a", function (event) {
    event.preventDefault();
    console.log(this.getAttribute("addressId"));
  });
});
